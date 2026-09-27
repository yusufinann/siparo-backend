package com.siparo.order;

/**
 * Kapıda (veya gel-al siparişlerinde restoranda) ödeme. Online kart ödemesi bir ödeme sağlayıcısı entegrasyonu
 * gerektirir; sağlayıcı yapılandırılmadan ONLINE_CARD kabul edilmez (bkz. PaymentGateway).
 */
public enum PaymentMethod {
    CASH_ON_DELIVERY,
    CARD_ON_DELIVERY,
    /** Yemek kartı (Sodexo, Multinet…) ile kapıda ödeme; restoranın kabul ettiği markalar Restaurant.mealCards'tadır. */
    MEAL_CARD_ON_DELIVERY,
    ONLINE_CARD
}
