package com.financeassistant.financeassistant.service;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

@Service
public class TransactionEventPublisher {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    public void publishNewTransactions(Long companyId, List<Long> txnIds) {
        Map<String, Object> event = Map.of(
                "companyId", companyId,
                "txnIds", txnIds
        );
        rabbitTemplate.convertAndSend(
                "finance.exchange",
                "transactions.new",
                event
        );
    }
}