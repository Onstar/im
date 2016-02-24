package com.gerrydevstory.stockticker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import demo.im.rs.entity.Ready;
import demo.captcha.message.Consumer;
import demo.im.rs.entity.Command;
import demo.im.rs.entity.CommandAdapter;

public class WebsocketEndPoint extends TextWebSocketHandler implements ApplicationContextAware{

	static public final String USER = "USER";
	
	private static final Logger logger = LoggerFactory.getLogger(WebsocketEndPoint.class);
	private List<WebSocketSession> sessions = new ArrayList<WebSocketSession>();
	private List<Consumer> clients = new ArrayList<Consumer>();
	private int current = 0;
	
	private ApplicationContext ctx;
	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.ctx = applicationContext;
	}
	
	public List<String> getActiveUsers(){
		List<String> rtn = new ArrayList<String>();
		for(WebSocketSession session : this.sessions){
			
			String user = (String)session.getAttributes().get(USER);
			if( null != user )
				rtn.add(user);
			else
				rtn.add("DEFAULT USER");
		}
		return rtn;
	}
	
	@Override
	public void afterConnectionEstablished(WebSocketSession session) throws Exception {
		
		logger.debug("connection established......");
		sessions.add(session);
		super.afterConnectionEstablished(session);
	}
	
	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {

		logger.debug("connection({}) lost......", session.getAttributes().get(USER));
		for(int i=this.clients.size()-1; i>=0; i--){
			Consumer client = (Consumer)this.clients.get(i);
			if(client.getUser().equals(session.getAttributes().get(USER))){
				this.clients.remove(i);
				client.stop();
			}
		}
		sessions.remove(session);
		super.afterConnectionClosed(session, status);
	}
	
	@Override  
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {

		logger.info("handleMessage({})", session.getAttributes().get(USER) == null ? "DEFAULT USER" : session.getAttributes().get(USER));
		super.handleTextMessage(session, message);

		Gson gson = new GsonBuilder().registerTypeAdapter(Command.class, new CommandAdapter()).create();
	    Command ack = gson.fromJson(message.getPayload(), Command.class);
	    
	    logger.info("handleMessage({})", ack.getCategory());
		if("READY".equals(ack.getCategory())){//ready
			
			Ready ready = (Ready)ack;
			session.getAttributes().put(USER, ready.getUser());
			logger.info("{} isReady", session.getAttributes().get(USER));
			
			//如果设置为轮询，下面的代码不需要
			JmsTemplate jmsTemplate = (JmsTemplate)this.ctx.getBean("jmsTemplate.request.consumer");
			Consumer client = new Consumer(jmsTemplate, session);
			this.clients.add(client);
			client.ready();
		}
		if("MESSAGE".equals(ack.getCategory())){

	        TextMessage returnMessage = new TextMessage(message.getPayload());
	        for(WebSocketSession sess : this.sessions){
	        	sess.sendMessage(returnMessage);
	        }
	        logger.info("\"{}\" broadCast", message.getPayload());
		}
    }
	
	public void broadcast(Command command) throws IOException{
		
		String message = new com.google.gson.Gson().toJson(command);
		TextMessage msg = new TextMessage(message.getBytes());
		for(WebSocketSession session : this.sessions)
			session.sendMessage(msg);
	}
	
	public void send2User(Command command, String user) throws IOException{
		
		String message = new com.google.gson.Gson().toJson(command);
		TextMessage msg = new TextMessage(message.getBytes());
		for(WebSocketSession session : this.sessions)
			if(session.getAttributes().get(USER) != null && session.getAttributes().get(USER).equals(user)) 
				session.sendMessage(msg);
	}
	
	/***
	 * 轮询
	 * @param command
	 * @throws IOException
	 */
	public void dispatch(Command command) throws IOException{

		GsonBuilder gson = new GsonBuilder();
		gson.setDateFormat("HH:mm:ss");
		String message = gson.create().toJson(command);
		TextMessage msg = new TextMessage(message.getBytes());
		if(this.sessions.size() > 0){
			
			int idx = (++this.current) % this.sessions.size();
			WebSocketSession user = this.sessions.get(idx);
			synchronized(user){
				logger.debug("dispatch COMMAND to user:{}", user.getAttributes().get(USER));
				user.sendMessage(msg);
			}
		}
	}
}
