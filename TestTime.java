import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class TestTime {
    public static void main(String[] args) {
        String workingHours = "09:00-02:00";
        String[] parts = workingHours.split("-");
        LocalTime startTime = LocalTime.parse(parts[0].trim(), DateTimeFormatter.ofPattern("HH:mm"));
        LocalTime endTime = LocalTime.parse(parts[1].trim(), DateTimeFormatter.ofPattern("HH:mm"));
        LocalTime now = LocalTime.now(ZoneId.of("Europe/Istanbul"));
        
        System.out.println("Now: " + now);
        System.out.println("Start: " + startTime);
        System.out.println("End: " + endTime);
        
        boolean result;
        if (endTime.isBefore(startTime)) {
            result = now.isAfter(startTime) || now.isBefore(endTime) || now.equals(startTime) || now.equals(endTime);
        } else {
            result = (now.isAfter(startTime) || now.equals(startTime)) && (now.isBefore(endTime) || now.equals(endTime));
        }
        System.out.println("Result: " + result);
    }
}
