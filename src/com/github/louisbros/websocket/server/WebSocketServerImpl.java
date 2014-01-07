package com.github.louisbros.websocket.server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONObject;

public class WebSocketServerImpl extends AbstractWebSocketServer{

	@Override
	public void broadcast(String message) throws IOException{
		
		Map<String, String > jsonMap = new HashMap<String, String>();
		jsonMap.put("peerSize", Integer.toString(peers.size()));
		jsonMap.put("message", message);
		ByteBuffer buffer = ProtocolUtils.encodeUnmaskedFrame(new JSONObject(jsonMap).toString());
		buffer.flip();
		
		for(Peer peer : peers){
			buffer.rewind();
			while(buffer.hasRemaining()){
				peer.getChannel().write(buffer);
			}
		}
	}
}
