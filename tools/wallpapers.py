"""Готовит фоны «Витрины» из фото владельца: один общий кадр 16:9 для всех четырёх, 1280×720.

Кадр задаёт ночное фото (центр, 16:9). Остальные совмещаются с ним по контурам гор и берега
(масштаб s и сдвиг ox, oy в долях ширины; замер — .proto/align.json, скрипт замера — в журнале MEMORY).
Запуск: python3 -I tools/wallpapers.py <папка с фото> <align.json>
"""
import json
import os
import sys

from PIL import Image

SRC = {'night': 'НОЧЬ.jpeg', 'evening': 'ВЕЧЕР.jpg', 'morning': 'УТРО.jpeg', 'day': 'ДЕНЬ.jpeg'}
OUT_W, OUT_H = 1280, 720
ASPECT = OUT_H / OUT_W
QUALITY = 88
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', 'app', 'src', 'main', 'assets', 'wallpapers')


def main(folder, align_path):
    align = json.load(open(align_path))
    align['night'] = {'s': 1.0, 'ox': 0.0, 'oy': 0.0}
    ref = Image.open(os.path.join(folder, SRC['night']))
    ref_h = ref.height / ref.width                 # высота ночного кадра в долях ширины
    y0, y1 = ref_h / 2 - ASPECT / 2, ref_h / 2 + ASPECT / 2   # кадр 16:9 по центру ночи
    for key, name in SRC.items():
        im = Image.open(os.path.join(folder, name)).convert('RGB')
        s, ox, oy = align[key]['s'], align[key]['ox'], align[key]['oy']
        h = im.height / im.width
        # Переносим углы общего кадра из координат ночи в координаты этого фото.
        box = [((0 - .5) * s + .5 + ox), ((y0 - ref_h / 2) * s + h / 2 + oy),
               ((1 - .5) * s + .5 + ox), ((y1 - ref_h / 2) * s + h / 2 + oy)]
        if box[0] < 0 or box[1] < 0 or box[2] > 1 or box[3] > h:
            sys.exit(f'{key}: общий кадр выходит за край фото {box}')
        px = [round(v * im.width) for v in box]
        im.crop(px).resize((OUT_W, OUT_H), Image.LANCZOS).save(os.path.join(OUT, key + '.jpg'), quality=QUALITY, optimize=True)
        print(f'{key:8s} {name:11s} {im.width}x{im.height}  кадр {px}  ×{s}')


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
