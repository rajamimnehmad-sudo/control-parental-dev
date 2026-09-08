"""Read-only physical health sampling. Does not navigate or touch the screen."""
import datetime,json,pathlib,subprocess,sys,time
serial='R58T34V31AE'
minutes=int(sys.argv[1]); output=pathlib.Path(sys.argv[2]); pid=sys.argv[3]
def adb(*args):
    try:
        p=subprocess.run(['adb','-s',serial,*args],text=True,capture_output=True,timeout=18)
        return (p.stdout+p.stderr).strip()
    except subprocess.TimeoutExpired:return 'UNAVAILABLE: timed out'
start=time.monotonic()
with output.open('w') as stream:
    for i in range(minutes+1):
        row={'utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'elapsed_s':round(time.monotonic()-start,1)}
        adb('shell','am','broadcast','-n','com.contentfilter.user.dev/com.contentfilter.user.chromedataplane.ChromePhotosDataPlaneLabReceiver','-a','com.contentfilter.user.chromedataplane.command.STATUS')
        row['memory']='\n'.join(x for x in adb('shell','cat','/proc/meminfo').splitlines() if x.startswith(('MemAvailable:','MemFree:','SwapFree:')))
        row['thermal']='\n'.join(x for x in adb('shell','dumpsys','thermalservice').splitlines() if 'Thermal Status:' in x)
        row['battery']='\n'.join(x for x in adb('shell','dumpsys','battery').splitlines() if any(k in x for k in ('level:','temperature:','Charge counter:','powered:')))
        row['cpu_ticks']=adb('shell','cat',f'/proc/{pid}/stat')
        row['process']='\n'.join(x for x in adb('shell','cat',f'/proc/{pid}/status').splitlines() if x.startswith(('VmRSS:','VmSize:','Threads:','FDSize:')))
        if i%5==0:
            for name in ['com.contentfilter.user.dev','com.android.chrome']:
                row[name]='\n'.join(x for x in adb('shell','dumpsys','meminfo',name).splitlines() if any(k in x for k in ('MEMINFO in pid','TOTAL PSS:','TOTAL RSS:','TOTAL SWAP PSS:')))
            renderers=[]
            for process in adb('shell','ps','-A','-o','PID,NAME').splitlines():
                fields=process.split()
                if len(fields)==2 and 'com.android.chrome:sandboxed_process' in fields[1]:
                    summary=adb('shell','dumpsys','meminfo',fields[0])
                    renderers.append({'pid':fields[0],'memory':'\n'.join(x for x in summary.splitlines() if 'TOTAL PSS:' in x)})
            row['chrome_renderers']=renderers
            row['cpu']=adb('shell','top','-b','-n','1','-m','12')
        stream.write(json.dumps(row)+'\n');stream.flush()
        print(json.dumps({'elapsed_s':row['elapsed_s'],'memory':row['memory'],'thermal':row['thermal']}),flush=True)
        if i<minutes:time.sleep(max(0,start+(i+1)*60-time.monotonic()))
