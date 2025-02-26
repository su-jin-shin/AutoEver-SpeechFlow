package com.demo.solace;

import com.solacesystems.jcsmp.*;
import org.springframework.stereotype.Service;

@Service
public class TopicPublisher {

    private final SpringJCSMPFactory solaceFactory;

    public TopicPublisher(SpringJCSMPFactory solaceFactory) {
        this.solaceFactory = solaceFactory;
    }

    public void publishMessage(String topicName, String messageText) {
        try {
            JCSMPSession session = solaceFactory.createSession();
            session.connect();

            Topic topic = JCSMPFactory.onlyInstance().createTopic(topicName);

            XMLMessageProducer producer = session.getMessageProducer(new JCSMPStreamingPublishCorrelatingEventHandler() {
                @Override
                public void responseReceivedEx(Object key) {
                    System.out.println("Solace 메시지 전송 성공: " + key.toString());
                }

                @Override
                public void handleErrorEx(Object key, JCSMPException cause, long timestamp) {
                    System.out.printf("[ERROR] Solace 메시지 전송 실패: %s@%s - %s%n", key.toString(), timestamp, cause);
                }
            });

            TextMessage msg = JCSMPFactory.onlyInstance().createMessage(TextMessage.class);
            msg.setText(messageText);
            //msg.setDeliveryMode(DeliveryMode.PERSISTENT);
            producer.send(msg, topic);

            System.out.println("Solace로 전송 완료: " + messageText + " > " + topicName);
            session.closeSession();
        } catch (JCSMPException e) {
            e.printStackTrace();
            System.err.println("[ERROR] Solace 메시지 전송 실패: " + e.getMessage());
        }
    }
}
