import sys, glob, os
from PIL import Image
d=sys.argv[1]; out=sys.argv[2]; names=sys.argv[3:] 
files=[os.path.join(d,n+".png") for n in names] if names else sorted(glob.glob(d+"/*.png"))
cols=int(os.environ.get("COLS",6)); T=int(os.environ.get("T",300))
rows=(len(files)+cols-1)//cols
sh=Image.new("RGB",(cols*T,rows*T),(14,12,36))
for i,f in enumerate(files):
    sh.paste(Image.open(f).convert("RGB").resize((T,T)),((i%cols)*T,(i//cols)*T))
sh.save(out)
