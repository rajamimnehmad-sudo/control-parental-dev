"""Bounded loopback/ADB fixture probe, not a Chrome latency or TLS trust gate."""
import concurrent.futures, datetime, hashlib, json, socket, ssl, sys, time
from pathlib import Path

def tunnel():
    raw=socket.create_connection(('127.0.0.1',19227),timeout=25)
    raw.sendall(b'CONNECT glosh-photos.test:443 HTTP/1.1\r\nHost: glosh-photos.test:443\r\n\r\n')
    head=b''
    while b'\r\n\r\n' not in head:
        byte=raw.recv(1)
        if not byte:raise RuntimeError('CONNECT closed')
        head+=byte
        if len(head)>8192:raise RuntimeError('CONNECT header bound')
    if not head.startswith(b'HTTP/1.1 200 '):raise RuntimeError('CONNECT rejected')
    # Only the local fixture via an owned ADB forward; never used for a real-site request.
    # Official Chrome trust is validated independently with its installed CA and normal browsing.
    context=ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
    context.check_hostname=False
    context.verify_mode=ssl.CERT_NONE
    context.set_alpn_protocols(['http/1.1'])
    return context.wrap_socket(raw,server_hostname='glosh-photos.test')

def idle():
    with tunnel() as connection:
        start=time.monotonic();data=connection.recv(1)
        return {'case':'TLS idle before request','elapsed_s':round(time.monotonic()-start,3),'clean_eof':data==b''}

def active():
    with tunnel() as connection:
        start=time.monotonic();connection.sendall(b'P');time.sleep(3)
        connection.sendall(b'OST /web11a/echo HTTP/1.1\r\nHost: glosh-photos.test\r\nContent-Type: application/octet-stream\r\nContent-Length: 5\r\nConnection: close\r\n\r\na=')
        time.sleep(3);connection.sendall(b'b&c');reply=b''
        while True:
            data=connection.recv(4096)
            if not data:break
            reply+=data
            if len(reply)>65536:raise RuntimeError('response bound')
        head,body=reply.split(b'\r\n\r\n',1)
        return {'case':'active partial headers and upload','elapsed_s':round(time.monotonic()-start,3),'status':head.split(b'\r\n',1)[0].decode('ascii'),'body_exact':body==b'a=b&c','body_sha256':hashlib.sha256(body).hexdigest()}

with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
    results=list(pool.map(lambda fn:fn(),[idle,active]))
record={'utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'version_code':int(sys.argv[1]),'results':results}
Path(sys.argv[2]).write_text(json.dumps(record,indent=2)+'\n');print(json.dumps(record),flush=True)
