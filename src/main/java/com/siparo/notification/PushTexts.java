package com.siparo.notification;

import java.util.Map;

/** Push bildirim metinleri (başlık, gövde). Uygulama içi bildirim merkezi metinleri istemcide üretilir. */
final class PushTexts {
    private PushTexts() {}

    static String[] render(String type, Map<String, Object> params, String locale) {
        boolean en = "en".equals(locale);
        String restaurant = String.valueOf(params.getOrDefault("restaurantName", "Siparo"));
        String number = params.get("orderNumber") == null ? "" : " #SP-" + params.get("orderNumber");
        return switch (type) {
            case "ORDER_ACCEPTED" -> en
                    ? new String[]{"Your order is being prepared", restaurant + " accepted your order" + number + "."}
                    : new String[]{"Siparişin hazırlanıyor", restaurant + " siparişini onayladı" + number + "."};
            case "ORDER_READY" -> en
                    ? new String[]{"Ready for pickup", "Your order" + number + " is ready at " + restaurant + "."}
                    : new String[]{"Teslim almaya hazır", restaurant + " siparişin" + number + " hazır."};
            case "ORDER_ON_THE_WAY" -> en
                    ? new String[]{"Your order is on the way", restaurant + " has sent your order" + number + "."}
                    : new String[]{"Siparişin yolda", restaurant + " siparişini" + number + " yola çıkardı."};
            case "ORDER_DELIVERED" -> en
                    ? new String[]{"Enjoy your meal!", "How was your order from " + restaurant + "? Rate it in the app."}
                    : new String[]{"Afiyet olsun!", restaurant + " siparişin nasıldı? Uygulamadan değerlendirebilirsin."};
            case "ORDER_CANCELLED" -> en
                    ? new String[]{"Order cancelled", restaurant + " cancelled your order" + number + "."}
                    : new String[]{"Sipariş iptal edildi", restaurant + " siparişini" + number + " iptal etti."};
            case "CAMPAIGN" -> en
                    ? new String[]{restaurant + ": " + params.getOrDefault("title", "New offer"), "Use code " + params.getOrDefault("code", "") + " on your next direct order."}
                    : new String[]{restaurant + ": " + params.getOrDefault("title", "Yeni kampanya"), "Bir sonraki direkt siparişinde " + params.getOrDefault("code", "") + " kodunu kullan."};
            case "ISSUE_RESOLVED" -> en
                    ? new String[]{"Your issue was resolved", restaurant + " responded to your report" + number + "."}
                    : new String[]{"Bildirimin çözüldü", restaurant + " bildirdiğin soruna yanıt verdi" + number + "."};
            case "DELIVERY_ASSIGNED" -> en
                    ? new String[]{"New delivery", restaurant + number + " is assigned to you."}
                    : new String[]{"Yeni teslimat", restaurant + number + " sana atandı."};
            case "DELIVERY_CANCELLED" -> en
                    ? new String[]{"Delivery cancelled", restaurant + number + " was cancelled."}
                    : new String[]{"Teslimat iptal edildi", restaurant + number + " iptal edildi."};
            default -> new String[]{"Siparo", restaurant};
        };
    }
}
