package demo.captcha.firstready;

import java.io.IOException;

import org.springframework.jms.core.JmsTemplate;
import org.springframework.web.socket.WebSocketSession;

import demo.captcha.IConsumer;
import demo.captcha.IProcessor;
import demo.im.rs.entity.Captcha;

/***
 * ready才能收到请求
 * @author martin
 *
 */
public class Processor extends IProcessor {
	
	private JmsTemplate requestSender;
	public void setRequestSender(JmsTemplate jms){ this.requestSender = jms; }
	
	@Override
	public IConsumer createConsumer(JmsTemplate jms, WebSocketSession session) {
		return new Consumer(jms, session);
	}
	
	@Override
	public void dispatch(Captcha captcha) throws IOException, Exception {
		
		this.requestSender.convertAndSend(captcha);
	}
}
