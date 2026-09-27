-- Yemek kartı markaları ödeme yönteminden ayrı saklanır; müşteri "Kapıda yemek kartı" seçeneğinin altında görür.
ALTER TABLE restaurants ADD COLUMN meal_cards VARCHAR(255);

-- V21'den sonra web paneli ödeme yöntemlerini yine Türkçe etiketlerle kaydetti ("Nakit", "Kredi Kartı", "Sodexo"...).
-- Etiketleri kanonik PaymentMethod / MealCard değerlerine dönüştür. SET ifadeleri eski satır değerini okur.
-- Kanonik değerler tam token olarak aranır (CARD_ON_DELIVERY, MEAL_CARD_ON_DELIVERY içinde yanlış eşleşmesin).
UPDATE restaurants
SET meal_cards = NULLIF(CONCAT_WS(',',
        CASE WHEN payment_methods ILIKE '%Sodexo%' THEN 'SODEXO' END,
        CASE WHEN payment_methods ILIKE '%Ticket Restaurant%' OR payment_methods ILIKE '%TICKET_RESTAURANT%' THEN 'TICKET_RESTAURANT' END,
        CASE WHEN payment_methods ILIKE '%Multinet%' THEN 'MULTINET' END,
        CASE WHEN payment_methods ILIKE '%Setcard%' THEN 'SETCARD' END,
        CASE WHEN payment_methods ILIKE '%Metropol%' THEN 'METROPOL' END
    ), ''),
    payment_methods = NULLIF(CONCAT_WS(',',
        CASE
            WHEN payment_methods ~ '(^|,)\s*CASH_ON_DELIVERY\s*(,|$)'
              OR payment_methods ILIKE '%Nakit%'
            THEN 'CASH_ON_DELIVERY'
        END,
        CASE
            WHEN payment_methods ~ '(^|,)\s*CARD_ON_DELIVERY\s*(,|$)'
              OR payment_methods ILIKE '%Kredi Kartı%'
              OR payment_methods ILIKE '%Banka Kartı%'
            THEN 'CARD_ON_DELIVERY'
        END,
        CASE
            WHEN payment_methods ~ '(^|,)\s*MEAL_CARD_ON_DELIVERY\s*(,|$)'
              OR payment_methods ILIKE '%Sodexo%'
              OR payment_methods ILIKE '%Ticket Restaurant%'
              OR payment_methods ILIKE '%TICKET_RESTAURANT%'
              OR payment_methods ILIKE '%Multinet%'
              OR payment_methods ILIKE '%Setcard%'
              OR payment_methods ILIKE '%Metropol%'
            THEN 'MEAL_CARD_ON_DELIVERY'
        END,
        CASE
            WHEN payment_methods ~ '(^|,)\s*ONLINE_CARD\s*(,|$)'
            THEN 'ONLINE_CARD'
        END
    ), '')
WHERE payment_methods IS NOT NULL
  AND BTRIM(payment_methods) <> '';
