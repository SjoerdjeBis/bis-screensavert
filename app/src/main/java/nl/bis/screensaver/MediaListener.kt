package nl.bis.screensaver

import android.service.notification.NotificationListenerService

/**
 * Doet zelf niets. Android geeft alleen apps met deze "meldingstoegang" inzage in wat
 * er speelt; het installatiescript zet die toegang eenmalig aan.
 */
class MediaListener : NotificationListenerService()
