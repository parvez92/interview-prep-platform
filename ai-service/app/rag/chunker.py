from dataclasses import dataclass

_CHUNK_SIZE = 400
_OVERLAP = 80


@dataclass
class Chunk:
    text: str
    index: int


def chunk_text(text: str, chunk_size: int = _CHUNK_SIZE, overlap: int = _OVERLAP) -> list[Chunk]:
    words = text.split()
    if not words:
        return []

    chunks: list[Chunk] = []
    start = 0
    idx = 0

    while start < len(words):
        end = min(start + chunk_size, len(words))
        chunk_words = words[start:end]
        chunks.append(Chunk(text=" ".join(chunk_words), index=idx))
        idx += 1
        if end >= len(words):
            break
        start = end - overlap

    return chunks
