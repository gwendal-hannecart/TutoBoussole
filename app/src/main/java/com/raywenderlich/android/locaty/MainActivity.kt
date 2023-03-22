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

import android.content.IntentFilter
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.raywenderlich.android.locaty.databinding.ActivityMainBinding
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    lateinit var binding: ActivityMainBinding


    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppTheme)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver,  IntentFilter(LocatyService.KEY_ON_SENSOR_CHANGED_ACTION))
    }

    override fun onResume() {
        super.onResume()
        startForegroundServiceForSensors(false) //false parce que foreground
    }

    private fun startForegroundServiceForSensors(background: Boolean) {
        // Création d'un Intent pour le service
        val locatyIntent = Intent(this, LocatyService::class.java)
        locatyIntent.putExtra(LocatyService.KEY_BACKGROUND, background)
        // Démarrage du service "foreground
        ContextCompat.startForegroundService(this, locatyIntent)
    }

    override fun onPause() {
        super.onPause()
        startForegroundServiceForSensors(true) //true car plus foreground
    }

    override fun onDestroy() {
        // unregisterd BroadcastReceiver qaund on en n'a plus besoin
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver)
        super.onDestroy()
    }

    private val broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Recuperation des données et assignation aux vues
            val direction = intent.getStringExtra(LocatyService.KEY_DIRECTION)
            val angle = intent.getDoubleExtra(LocatyService.KEY_ANGLE,0.0)
            val angleWithDirection = "$angle  $direction"
            binding.directionTextView.text = angleWithDirection
            // Reflexion de l'angle car angle inverse au sens des aiguilles d'une montre alors que vues Android tourne dans le sens des aiguilles d'une montre
            binding.compassImageView.rotation = angle.toFloat() * -1
        }
    }
}

//tutoriel https://www.kodeco.com/10838302-sensors-tutorial-for-android-getting-started