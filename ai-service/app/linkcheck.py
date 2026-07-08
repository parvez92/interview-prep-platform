import asyncio
import logging

import httpx

log = logging.getLogger(__name__)

_UA = {"User-Agent": "Mozilla/5.0 (compatible; prep-loop link check)"}
# gated-but-existing responses: bot walls and auth gates prove the URL is real
_ALIVE_STATUSES = {401, 403, 405, 429}


async def filter_live_urls(items: list[dict], url_key: str = "url",
                           timeout: float = 5.0, concurrency: int = 8) -> list[dict]:
    """
    Drop items whose URL is verifiably dead (DNS failure, connection refused,
    404/410). Items without a URL pass through untouched. Benefit of the doubt
    on timeouts and bot-gated statuses — false drops are worse than a slow site.
    """
    to_check = [(i, item) for i, item in enumerate(items)
                if isinstance(item.get(url_key), str) and item[url_key].startswith("http")]
    if not to_check:
        return items

    sem = asyncio.Semaphore(concurrency)

    async def alive(client: httpx.AsyncClient, url: str) -> bool:
        async with sem:
            try:
                resp = await client.head(url)
                if resp.status_code >= 400 and resp.status_code not in _ALIVE_STATUSES:
                    # many sites reject HEAD — confirm with a GET before declaring dead
                    resp = await client.get(url)
                return resp.status_code < 400 or resp.status_code in _ALIVE_STATUSES
            except httpx.TimeoutException:
                return True  # slow is not dead
            except httpx.HTTPError:
                return False  # DNS failure / connection refused → hallucinated or gone

    async with httpx.AsyncClient(follow_redirects=True, timeout=timeout, headers=_UA) as client:
        checks = await asyncio.gather(*(alive(client, item[url_key]) for _, item in to_check))

    dead = {idx for (idx, item), ok in zip(to_check, checks) if not ok}
    if dead:
        log.info("link-check dropped %d dead url(s): %s",
                 len(dead), [items[i][url_key] for i in sorted(dead)][:5])
    return [item for i, item in enumerate(items) if i not in dead]
