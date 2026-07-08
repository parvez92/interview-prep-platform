import { useEffect, useState } from 'react';

const FRAMES = ['（・・？）', '（＿＿）zzZ', '（°口°）！', '（＾▽＾）✧', '（ˊ . ˋ）'];
const THOUGHTS = ['…', '！？', '考中…', 'ｺﾝﾋﾟｭｰﾀ', '…'];

const CSS = `
@keyframes bubble-float {
  0%   { transform: translateY(0)   scale(1);    opacity: 0; }
  15%  { opacity: 1; }
  85%  { opacity: 1; }
  100% { transform: translateY(-48px) scale(.8); opacity: 0; }
}
@keyframes sparkle-spin {
  0%   { transform: rotate(0deg)   scale(1); }
  50%  { transform: rotate(180deg) scale(1.3); }
  100% { transform: rotate(360deg) scale(1); }
}
@keyframes blink-eyes {
  0%, 90%, 100% { transform: scaleY(1); }
  95%           { transform: scaleY(.05); }
}
@keyframes sweat-drop {
  0%, 70%  { opacity: 0; transform: translateY(-4px); }
  80%      { opacity: 1; transform: translateY(0); }
  100%     { opacity: 0; transform: translateY(6px); }
}
@keyframes face-tilt {
  0%, 100% { transform: rotate(0deg); }
  25%      { transform: rotate(-5deg); }
  75%      { transform: rotate(5deg); }
}
@keyframes dot-pulse {
  0%, 100% { opacity: .3; }
  50%      { opacity: 1; }
}
`;

export function AnimeThinking() {
  const [frame, setFrame] = useState(0);

  useEffect(() => {
    const t = setInterval(() => setFrame(f => (f + 1) % FRAMES.length), 1800);
    return () => clearInterval(t);
  }, []);

  return (
    <>
      <style>{CSS}</style>

      <div style={{ position: 'relative', width: 100, height: 100, userSelect: 'none' }}>

        {/* Floating thought bubble */}
        <div style={{
          position: 'absolute', top: -10, left: '50%',
          transform: 'translateX(-50%)',
          animation: 'bubble-float 1.8s ease-in-out infinite',
          background: 'var(--surface-1, #16182a)',
          border: '1.5px solid var(--accent, #7c6cff)',
          borderRadius: 20,
          padding: '4px 10px',
          fontSize: '0.7rem',
          color: 'var(--accent, #7c6cff)',
          whiteSpace: 'nowrap',
          pointerEvents: 'none',
        }}>
          {THOUGHTS[frame]}
        </div>

        {/* Spinning sparkle star top-right */}
        <div style={{
          position: 'absolute', top: 6, right: 4,
          fontSize: '0.9rem',
          animation: 'sparkle-spin 2s linear infinite',
          pointerEvents: 'none',
        }}>
          ✦
        </div>

        {/* Main face — tilts while thinking */}
        <div style={{
          width: 72, height: 72,
          margin: '14px auto 0',
          background: 'var(--surface-2, #1e2030)',
          border: '2px solid var(--accent, #7c6cff)',
          borderRadius: '50%',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          gap: 6,
          animation: 'face-tilt 3.6s ease-in-out infinite',
          position: 'relative',
          fontSize: '1.5rem',
        }}>
          {/* Sweat drop (shows on certain frames) */}
          {frame === 2 && (
            <div style={{
              position: 'absolute', top: 4, right: 10,
              fontSize: '0.65rem',
              animation: 'sweat-drop 1.8s ease forwards',
            }}>
              💧
            </div>
          )}

          {/* Eyes */}
          <div style={{
            display: 'flex', gap: 14, marginTop: 4,
          }}>
            {[0, 1].map(i => (
              <div key={i} style={{
                width: 11, height: 11,
                background: 'var(--accent, #7c6cff)',
                borderRadius: '50%',
                animation: `blink-eyes ${2 + i * 0.4}s ease-in-out infinite`,
              }} />
            ))}
          </div>

          {/* Mouth — changes with frame */}
          <div style={{
            fontSize: frame === 2 ? '0.9rem' : '0.55rem',
            letterSpacing: 1,
            lineHeight: 1,
            marginBottom: 6,
            transition: 'font-size .3s',
          }}>
            {frame === 0 && '–'}
            {frame === 1 && 'ᴗ'}
            {frame === 2 && '!'}
            {frame === 3 && '▽'}
            {frame === 4 && '•'}
          </div>
        </div>

        {/* Thinking dots below */}
        <div style={{
          display: 'flex', justifyContent: 'center', gap: 5, marginTop: 8,
        }}>
          {[0, 1, 2].map(i => (
            <div key={i} style={{
              width: 5, height: 5,
              background: 'var(--accent, #7c6cff)',
              borderRadius: '50%',
              animation: `dot-pulse 1.2s ease-in-out infinite`,
              animationDelay: `${i * 0.2}s`,
            }} />
          ))}
        </div>
      </div>
    </>
  );
}
