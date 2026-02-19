package com.example.springai.tool;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class DemoTools {

    @Tool(description = "Get the current date-time for a timezone.")
    public String currentDateTime(
            @ToolParam(description = "Timezone id like Asia/Tashkent, Europe/Berlin or UTC") String timezone) {

        String zone = StringUtils.hasText(timezone) ? timezone.trim() : "UTC";

        try {
            ZoneId zoneId = ZoneId.of(zone);
            return ZonedDateTime.now(zoneId).format(DateTimeFormatter.ISO_ZONED_DATE_TIME);
        }
        catch (Exception ex) {
            return "Invalid timezone: " + zone + ". Example: UTC or Asia/Tashkent";
        }
    }

    @Tool(description = "Convert distance from kilometers to miles.")
    public double kilometersToMiles(@ToolParam(description = "Distance in kilometers") double kilometers) {
        return Math.round(kilometers * 0.621371 * 1000.0) / 1000.0;
    }

    @Tool(description = "Calculate monthly loan payment with fixed interest.")
    public double loanMonthlyPayment(
            @ToolParam(description = "Principal amount, for example 10000") double principal,
            @ToolParam(description = "Yearly interest percent, for example 18") double annualInterestPercent,
            @ToolParam(description = "Loan term in months, for example 24") int months) {

        if (principal <= 0 || months <= 0) {
            return 0;
        }

        double monthlyRate = annualInterestPercent / 1200.0;
        if (monthlyRate == 0) {
            return Math.round((principal / months) * 100.0) / 100.0;
        }

        double factor = Math.pow(1 + monthlyRate, months);
        double payment = principal * (monthlyRate * factor) / (factor - 1);
        return Math.round(payment * 100.0) / 100.0;
    }
}
