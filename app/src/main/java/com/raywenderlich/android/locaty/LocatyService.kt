/**
 * Copyright (c) 2020 Razeware LLC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.raywenderlich.android.locaty

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorManager
import android.os.IBinder
import android.hardware.SensorEventListener
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.core.app.NotificationCompat
import kotlin.math.round

class LocatyService : Service() , SensorEventListener {
    private lateinit var  sensorManager :SensorManager //Sensor Manager acces differents capteurs (après listeneur pour recevoir données)
    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    // Rotation matrix afin que les coordonnées de la machine correspondent à celles de la réalité
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    // creation d'un set de clefs
    companion object {
        val KEY_ANGLE = "angle"
        val KEY_DIRECTION = "direction"
        val KEY_BACKGROUND = "background"
        val KEY_NOTIFICATION_ID = "notificationId"
        val KEY_ON_SENSOR_CHANGED_ACTION = "com.raywenderlich.android.locaty.ON_SENSOR_CHANGED"
        val KEY_NOTIFICATION_STOP_ACTION = "com.raywenderlich.android.locaty.NOTIFICATION_STOP"
    }

    //Notification Persistente
    private var background = false

    //variables utilisées Création PendingItent
    private val notificationActivityRequestCode = 0
    private val notificationId = 1
    private val notificationStopRequestCode = 2
    override fun onCreate() {
        super.onCreate()
        // getSystemeService retourne un gestionnaire, as pour casting
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        sensorManager.apply {
            // Enregistre un capteur event callback afin d'avoir les changements de l'acceleromete
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.also { accelerometer ->
                sensorManager.registerListener(this@LocatyService, accelerometer, SensorManager.SENSOR_DELAY_FASTEST, SensorManager.SENSOR_DELAY_FASTEST)
            }
            //Enregistre un capteur event callback afin d'écouter les changements du magnetometer.
            sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.also { magneticField ->
                sensorManager.registerListener(this@LocatyService, magneticField, SensorManager.SENSOR_DELAY_FASTEST, SensorManager.SENSOR_DELAY_FASTEST)
            }

        }

        // enregistrer notification

        // Creation d'une notification
        val notification = createNotification(getString(R.string.not_available), 0.0)
        // demarrage du service de notifications en foreground
        startForeground(notificationId, notification)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            // Obtenir l'etat de l'application depuis la MainActivity
            background = it.getBooleanExtra(KEY_BACKGROUND, false)
        }
        return START_STICKY
    }

    //Ecouter évenement les changements besoin implementer interface SensorEventListener et overwrite onAccuracyChanged et onSensorChanged

    //onAccuracyChanged appel que changement dans la précision
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Handle accuracy changes
    }

    //onSensorChanged à chaque fois nouvel event du capteur => tableaux 3D
    override fun onSensorChanged(event: SensorEvent?) {
        // Handle sensor changes
        if(event == null){
            return

        }
        //verifie le type du capteur
        //Puis copie des valeurs des valeurs dans des tableaux
        if(event.sensor.type == Sensor.TYPE_ACCELEROMETER){
            System.arraycopy(event.values,0, accelerometerReading,0, accelerometerReading.size)
        }else if (event.sensor.type==Sensor.TYPE_ACCELEROMETER){
            System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size)
        }
        updateOrientationAngles()
    }

    //rotation matrix
    fun updateOrientationAngles() {
        // Retrouve la rotation matrix
        SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReading, magnetometerReading)
        // Utilisation de la matrice avec le tableau de 9 valeurs et la carte de 3 valeurs
        /*
        * orientation[0] = Azimuth (rotation around the -ve z-axis)
        * orientation[1] = Pitch (rotation around the x-axis)
        * orientation[2] = Roll (rotation around the y-axis)
        * radian
        * */
        val orientation = SensorManager.getOrientation(rotationMatrix, orientationAngles)
        // Convertion de l'azimut en degrés
        val degrees = (Math.toDegrees(orientation.get(0).toDouble()) + 360.0) % 360.0
        // arrondi l'angle
        val angle = round(degrees * 100) / 100
        val direction = getDirection(degrees)

        // Création de l'intent
        val intent = Intent()
        intent.putExtra(KEY_ANGLE, angle)
        intent.putExtra(KEY_DIRECTION, direction)
        intent.action = KEY_ON_SENSOR_CHANGED_ACTION
        //envoie des données à l'activité main
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
        if (background) {
            // Notification créee et montrée quadn apps background
            val notification = createNotification(direction, angle)
            startForeground(notificationId, notification)
        } else {
            // cache notification quand apps foreground
            stopForeground(true)
        }
    }



    //determiner la direction que pointre l'utilisateur
    private fun getDirection(angle: Double): String {
        var direction = ""

        if (angle >= 350 || angle <= 10)
            direction = "N"
        if (angle < 350 && angle > 280)
            direction = "NW"
        if (angle <= 280 && angle > 260)
            direction = "W"
        if (angle <= 260 && angle > 190)
            direction = "SW"
        if (angle <= 190 && angle > 170)
            direction = "S"
        if (angle <= 170 && angle > 100)
            direction = "SE"
        if (angle <= 100 && angle > 80)
            direction = "E"
        if (angle <= 80 && angle > 10)
            direction = "NE"

        return direction
    }

    //create notification
    @SuppressLint("UnspecifiedImmutableFlag")
    private fun createNotification(direction: String, angle: Double): Notification {
        // Creation d'un NotifivationManager
        val notificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                application.packageName,
                "Notifications", NotificationManager.IMPORTANCE_DEFAULT
            )

            // Configure the notification channel.
            notificationChannel.enableLights(false)
            notificationChannel.setSound(null, null)
            notificationChannel.enableVibration(false)
            notificationChannel.vibrationPattern = longArrayOf(0L)
            notificationChannel.setShowBadge(false)
            notificationManager.createNotificationChannel(notificationChannel)
        }

        val notificationBuilder = NotificationCompat.Builder(baseContext, application.packageName)
        // Ouvre l'ecran principal en appuyant sur la notification
        val contentIntent = PendingIntent.getActivity(
            this, notificationActivityRequestCode,
            Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT)
        // AJoute un intent pour stopper apparition de la notification
        val stopNotificationIntent = Intent(this, WifiP2pManager.ActionListener::class.java)
        stopNotificationIntent.action = KEY_NOTIFICATION_STOP_ACTION
        stopNotificationIntent.putExtra(KEY_NOTIFICATION_ID, notificationId)
        val pendingStopNotificationIntent =
            PendingIntent.getBroadcast(this, notificationStopRequestCode, stopNotificationIntent, PendingIntent.FLAG_UPDATE_CURRENT)

        notificationBuilder.setAutoCancel(true)
            .setDefaults(Notification.DEFAULT_ALL)
            .setContentTitle(resources.getString(R.string.app_name))
            .setContentText("You're currently facing $direction at an angle of $angle°")
            .setWhen(System.currentTimeMillis())
            .setDefaults(0)
            .setVibrate(longArrayOf(0L))
            .setSound(null)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentIntent(contentIntent)
            .addAction(R.mipmap.ic_launcher_round, getString(R.string.stop_notifications), pendingStopNotificationIntent)


        return notificationBuilder.build()
    }

    //Creation d'un BroadcastRecevier pour arrêter affichace notification
    class ActionListener : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {

            if (intent != null && intent.action != null) {
                // verifie si l'action de broadcast est Stop Notifications
                if (intent.action.equals(KEY_NOTIFICATION_STOP_ACTION)) {
                    context?.let {
                        // reference a NotificationManager
                        val notificationManager =
                            context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                        val locatyIntent = Intent(context, LocatyService::class.java)
                        // Arrête du service
                        context.stopService(locatyIntent)
                        val notificationId = intent.getIntExtra(KEY_NOTIFICATION_ID, -1)
                        if (notificationId != -1) {
                            // retire la notification persitente du menu de notifications
                            notificationManager.cancel(notificationId)
                        }
                    }
                }
            }
        }
    }
}