-- Eski istemciler ödeme yöntemlerini Türkçe etiketlerle saklıyordu.
-- Sipariş sözleşmesi PaymentMethod enum değerlerini kullandığı için mevcut
-- restoran kayıtlarını kanonik değerlere dönüştür.
UPDATE restaurants
SET payment_methods = NULLIF(CONCAT_WS(',',
    CASE
        WHEN payment_methods ILIKE '%CASH_ON_DELIVERY%'
          OR payment_methods ILIKE '%Nakit%'
        THEN 'CASH_ON_DELIVERY'
    END,
    CASE
        WHEN payment_methods ILIKE '%CARD_ON_DELIVERY%'
          OR payment_methods ILIKE '%Kredi Kartı%'
          OR payment_methods ILIKE '%Banka Kartı%'
        THEN 'CARD_ON_DELIVERY'
    END,
    CASE
        WHEN payment_methods ILIKE '%ONLINE_CARD%'
        THEN 'ONLINE_CARD'
    END
), '')
WHERE payment_methods IS NOT NULL
  AND BTRIM(payment_methods) <> ''
  AND payment_methods !~ '^(CASH_ON_DELIVERY|CARD_ON_DELIVERY|ONLINE_CARD)(,(CASH_ON_DELIVERY|CARD_ON_DELIVERY|ONLINE_CARD))*$';
