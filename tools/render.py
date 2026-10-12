import sys, os, time, traceback
sys.path.insert(0, os.path.dirname(__file__))
from PIL import Image
import scenes

names = sys.argv[2:] or list(scenes.SCENES)
out_dir = sys.argv[1]
os.makedirs(out_dir, exist_ok=True)
for n in names:
    t = time.time()
    try:
        im = scenes.SCENES[n]()
        im.resize((480, 480), Image.LANCZOS).save(os.path.join(out_dir, n + ".png"))
        print(f"ok {n} {time.time()-t:.1f}s")
    except Exception:
        print("ERR", n); traceback.print_exc(limit=3)
