"""Bounded runtime cache comparison; keeps Chrome data and model unchanged."""
import subprocess,json,time,os,re,datetime
from pathlib import Path
p=Path(__file__).parent
serial="R58T34V31AE"
component="com.contentfilter.user.dev/com.contentfilter.user.chromedataplane.ChromePhotosDataPlaneLabReceiver"
def adb(*a):return subprocess.run(["adb","-s",serial,*a],capture_output=True,text=True,timeout=20).stdout
def command(a,*extra):return adb("shell","am","broadcast","-n",component,"-a","com.contentfilter.user.chromedataplane.command."+a,*extra)
rows=[]
for capacity in (64,256):
 command("STOP");time.sleep(2)
 command("START","--ez","full_tunnel_dev_gate_enabled","true","--ez","stock_media_authority_enabled","true","--ez","document_self_shield_enabled","true","--ei","chrome_photo_decision_concurrency","1","--ei","chrome_photo_decision_cache_entries",str(capacity))
 time.sleep(7)
 adb("shell","input","keyevent","KEYCODE_WAKEUP");adb("shell","wm","dismiss-keyguard")
 adb("shell","am","start","-n","com.android.chrome/com.google.android.apps.chrome.Main");time.sleep(3)
 for i in range(3):
  subprocess.run(["node",str(p/"navigate.mjs"),"713","https://www.fravega.com/"],capture_output=True,check=True,timeout=20)
  time.sleep(12)
  expr='({ttfb:performance.getEntriesByType("navigation")[0]?.responseStart,dcl:performance.getEntriesByType("navigation")[0]?.domContentLoadedEventEnd,load:performance.getEntriesByType("navigation")[0]?.loadEventEnd,decoded:[...document.images].filter(x=>x.naturalWidth>0).length})'
  r=subprocess.run(["node",str(p/"cdp.mjs")],input=expr,text=True,capture_output=True,env={**os.environ,"GLOSH_TAB_ID":"713"},timeout=15)
  command("STATUS");time.sleep(1)
  status=[l for l in adb("logcat","-d","-s","ChromePhotosDataPlane:I").splitlines() if "phase=status " in l][-1]
  row={"utc":datetime.datetime.now(datetime.timezone.utc).isoformat(),"capacity":capacity,"run":i,"page":json.loads(r.stdout),"status":status}
  rows.append(row)
  (p/"cache-453-alternating.json").write_text(json.dumps(rows,indent=2))
  print(json.dumps({"capacity":capacity,"run":i,"page":row["page"],"cache":re.findall(r"(?:cacheHits|cacheMisses|engineCalls|cacheEvictions)=\S+",status)}),flush=True)
