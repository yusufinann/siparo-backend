package com.siparo.order;

/**
 * Kapıda (veya gel-al siparişlerinde restoranda) ödeme. Online kart ödemesi bir ödeme sağlayıcısı entegrasyonu
 * gerektirir; sağlayıcı yapılandırılmadan ONLINE_CARD kabul edilmez (bkz. PaymentGateway).
 */
public enum PaymentMethod {
    CASH_ON_DELIVERY,
    CARD_ON_DELIVERY,
    ONLINE_CARD
}
