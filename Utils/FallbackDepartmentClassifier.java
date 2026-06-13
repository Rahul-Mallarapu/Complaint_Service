package com.smart_complaint_service.project.Utils;

public class FallbackDepartmentClassifier {

    public static String classify(String description, String serviceType) {

        if (description == null) description = "";
        if (serviceType == null) serviceType = "";

        String text = (description + " " + serviceType).toLowerCase();

        if (containsAny(text, "water", "pipe", "leak", "drain", "flood",
                "sewage", "plumb", "tap", "faucet", "toilet")) {
            return "WATER_MAINTENANCE";
        }

        if (containsAny(text, "electric", "power", "light", "wiring",
                "short circuit", "voltage", "generator", "blackout", "socket")) {
            return "ELECTRICAL_MAINTENANCE";
        }

        if (containsAny(text, "internet", "network", "wifi", "server",
                "software", "computer", "laptop", "system", "it ", "online",
                "website", "app", "login", "password", "database")) {
            return "IT_SUPPORT";
        }

        if (containsAny(text, "road", "building", "construction", "civil",
                "crack", "wall", "roof", "ceiling", "floor", "infrastructure")) {
            return "CIVIL_MAINTENANCE";
        }

        if (containsAny(text, "clean", "garbage", "waste", "sanit",
                "hygiene", "pest", "rat", "cockroach", "sweeping", "mop")) {
            return "HOUSEKEEPING";
        }

        if (containsAny(text, "security", "theft", "stolen", "break in",
                "cctv", "camera", "guard", "trespass", "unauthorised")) {
            return "SECURITY";
        }

        if (containsAny(text, "salary", "payroll", "leave", "hr ",
                "human resource", "attendance", "holiday", "reimburs")) {
            return "HR_ADMINISTRATION";
        }

        if (serviceType.equalsIgnoreCase("PHYSICAL")) {
            return "GENERAL_MAINTENANCE";
        }
        return "GENERAL_SUPPORT";
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }
}