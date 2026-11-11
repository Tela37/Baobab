package td.teladoum.baobab.utils;

import td.teladoum.baobab.R;

import td.teladoum.baobab.models.*;
import td.teladoum.baobab.adapters.*;
import td.teladoum.baobab.repositories.*;
import td.teladoum.baobab.services.*;
import td.teladoum.baobab.firebase.*;
import td.teladoum.baobab.helpers.*;
import td.teladoum.baobab.notifications.*;
import td.teladoum.baobab.utils.*;
import td.teladoum.baobab.activities.*;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Utilitaire de formatage et de comparaison d'horodatages.
 * Permet de vérifier si une date se situe dans une fenêtre temporelle (ex: stories éphémères de 24h).
 */
public class TimeUtils {

    public static boolean isWithinHours(String dateTimeStr, long hoursLimit) {
        if (dateTimeStr == null || dateTimeStr.trim().isEmpty()) {
            return true;
        }
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            Date date = sdf.parse(dateTimeStr);
            if (date == null) return true;

            long diffMillis = System.currentTimeMillis() - date.getTime();
            long limitMillis = hoursLimit * 3600 * 1000L;

            return diffMillis >= 0 && diffMillis <= limitMillis;
        } catch (Exception e) {
            return true;
        }
    }

    public static String getCurrentLocalDateTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        sdf.setTimeZone(java.util.TimeZone.getDefault());
        return sdf.format(new Date());
    }
}

