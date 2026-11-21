// Real network test of the WebSocket methods extracted verbatim from MainActivity.java.
// Requires Java (javac + java) and Node 22+. Android SDK is not needed for this test.
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import net from 'node:net';
import {spawn, execFileSync} from 'node:child_process';
import assert from 'node:assert/strict';
import {openTransport} from '../../LarpPlay/src/transport.js';
const android = fs.readFileSync(new URL('../app/src/main/java/io/github/bluecartooncat/larpproxy/MainActivity.java', import.meta.url), 'utf8');
function method(name) {
  const start = android.indexOf('        private static ' + name);
  assert.ok(start >= 0,`method missing: ${name}`);
  const open=android.indexOf('{',start);let depth=0;
  for(let i=open;i<android.length;i++) {
    if(android[i]==='{')depth++;
    if(android[i]==='}' && --depth===0) return android.slice(start,i+1);
  }
  throw Error(`Unclosed method ${name}`);
}
const methods=[
  'void acceptWebSocket','void websocketToTcp','void tcpToWebsocket',
  'int requireByte','void readAll','void writeWebSocketFrame'
].map(method).join('\n');
const java = `import java.io.*;import java.net.*;import java.util.*;import java.nio.charset.*;import java.security.*;
public class WsBridgeHarness {
  private static void closeQuietly(Closeable c) { if(c!=null)try{c.close();}catch(IOException ignored){} }
  ${methods}
  public static void main(String[] args) throws Exception {
    try(ServerSocket server=new ServerSocket(0,8,InetAddress.getByName("127.0.0.1"))) {
      System.out.println("LISTEN " + server.getLocalPort()); System.out.flush();
      Socket client=server.accept();
      try(Socket upstream=new Socket("127.0.0.1",Integer.parseInt(args[0]))) {
        acceptWebSocket(client);
        Thread a=new Thread(()->websocketToTcp(client,upstream));
        Thread b=new Thread(()->tcpToWebsocket(upstream,client));
        a.start();b.start();a.join();b.join();
      } finally {closeQuietly(client);}
    }
  }
}`;
const dir = fs.mkdtempSync(path.join(os.tmpdir(),'larp-ws-test-'));
const file = path.join(dir,'WsBridgeHarness.java');fs.writeFileSync(file,java);
let child,upstream;
try {
  execFileSync('javac',[file],{stdio:'pipe'});
  upstream=net.createServer(sock=>sock.pipe(sock));
  await new Promise(resolve=>upstream.listen(0,'127.0.0.1',resolve));
  child=spawn('java',['-cp',dir,'WsBridgeHarness',String(upstream.address().port)],{stdio:['ignore','pipe','pipe']});
  const listenPort = await Promise.race([
    new Promise((resolve,reject)=>{
      let output=''; child.stdout.on('data',data=>{
        output+=String(data);const match=output.match(/LISTEN (\d+)/);if(match)resolve(Number(match[1]));
      });child.once('exit',code=>reject(Error(`Java test exited ${code}`)));
    }),
    new Promise((_,reject)=>setTimeout(()=>reject(Error('Java harness did not start')),6000))
  ]);
  const transport=await openTransport('127.0.0.1',listenPort,'ws');
  const writer=transport.writable.getWriter(),reader=transport.readable.getReader();
  const payload=Uint8Array.from({length:150_000},(_,i)=>i*17%256);
  await writer.write(payload);
  let got=new Uint8Array();
  while(got.length<payload.length){
    const {value,done}=await Promise.race([reader.read(),new Promise((_,reject)=>setTimeout(()=>reject(Error('WebSocket echo timed out')),6000))]);
    assert.ok(!done,'WebSocket should remain open');
    const merged=new Uint8Array(got.length+value.length);merged.set(got);merged.set(value,got.length);got=merged;
  }
  assert.deepEqual(got,payload,'WebSocket framed traffic must preserve all bytes bidirectionally');
  transport.close();
  console.log('PASS: real Java WS upgrade + binary frame decoding/encoding + 150 KiB end-to-end echo');
} finally {
  child?.kill('SIGTERM');
  if(upstream) await new Promise(resolve=>upstream.close(resolve));
  fs.rmSync(dir,{recursive:true,force:true});
}
