"""Разбор `adb shell dumpsys gfxinfo <pkg> framestats`: медианы и 90-й перцентиль фаз кадра, мс.

Запуск: python3 -I tools/framestats.py < framestats.txt
"""
import statistics
import sys

lines = [line.strip().rstrip(',') for line in sys.stdin]
header = None
frames = []
for line in lines:
    if line.startswith('Flags,'):
        header = line.split(',')
    elif header and line[:1].isdigit() and line.count(',') >= len(header) - 2:
        values = list(map(int, line.split(',')))
        if values[0] == 0:          # 0 — обычный кадр; прочие флаги — первый кадр окна и т. п.
            frames.append(dict(zip(header, values)))

PHASES = [
    ('ввод и анимации', 'Vsync', 'PerformTraversalsStart'),
    ('раскладка', 'PerformTraversalsStart', 'DrawStart'),
    ('запись отрисовки', 'DrawStart', 'SyncQueued'),
    ('синхронизация', 'SyncStart', 'IssueDrawCommandsStart'),
    ('выдача команд GPU', 'IssueDrawCommandsStart', 'SwapBuffers'),
    ('swap', 'SwapBuffers', 'FrameCompleted'),
    ('GPU до готовности', 'IssueDrawCommandsStart', 'GpuCompleted'),
    ('кадр целиком', 'IntendedVsync', 'FrameCompleted'),
]


def span(a, b):
    return sorted((f[b] - f[a]) / 1e6 for f in frames if f.get(a, 0) > 0 and f.get(b, 0) > 0 and f[b] >= f[a])


print(f'кадров: {len(frames)}')
for name, a, b in PHASES:
    v = span(a, b)
    if v:
        print(f'{name:20s} медиана {statistics.median(v):6.1f}   90% {v[int(len(v) * 0.9) - 1 if len(v) > 1 else 0]:6.1f}')

# Шаг между соседними кадрами внутри одной анимации (паузы между нажатиями > 200 мс отбрасываются).
# На проекторе экран 30 Гц: шаг 33 мс — предел, 67 мс — пропущен кадр.
starts = sorted(f['IntendedVsync'] for f in frames)
steps = [(b - a) / 1e6 for a, b in zip(starts, starts[1:]) if (b - a) / 1e6 < 200]
if steps:
    steps.sort()
    hit = sum(1 for s in steps if s < 40) / len(steps)
    print(f'шаг кадров           медиана {statistics.median(steps):6.1f}   90% {steps[int(len(steps) * 0.9) - 1]:6.1f}   '
          f'кадров в такт 30 Гц: {hit:.0%}')
