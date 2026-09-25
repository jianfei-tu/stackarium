DELETE c FROM assistant_conversations c
WHERE NOT EXISTS (SELECT 1 FROM assistant_messages m WHERE m.conversation_id = c.id)
  AND NOT EXISTS (SELECT 1 FROM assistant_proposals p WHERE p.conversation_id = c.id);
