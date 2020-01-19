package com.example.wildfire_fixed_imports

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.view.Menu
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat

import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.wildfire_fixed_imports.com.example.wildfire_fixed_imports.*
import com.example.wildfire_fixed_imports.viewmodel.view_model_classes.MapViewModel
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions
import com.mapbox.mapboxsdk.location.LocationComponentOptions
import com.mapbox.mapboxsdk.location.modes.CameraMode
import com.mapbox.mapboxsdk.location.modes.RenderMode
import com.mapbox.mapboxsdk.maps.Style
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber


class MainActivity : AppCompatActivity() {

    private lateinit var mapViewModel: MapViewModel
    private lateinit var appBarConfiguration: AppBarConfiguration
    lateinit var fab: FloatingActionButton
    private lateinit var layout: View
    private val TAG:String
        get() = "$javaClass $methodName"


    private var locationManager: LocationManager? = null
    private lateinit var fusedLocationClient:FusedLocationProviderClient
    val applicationLevelProvider = ApplicationLevelProvider.getApplicaationLevelProviderInstance()


    @SuppressLint("NewApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        applicationLevelProvider.nav_view = findViewById(R.id.nav_view)
        //set this activity as the current activity in application level provider
        applicationLevelProvider.currentActivity = this
        fusedLocationClient=LocationServices.getFusedLocationProviderClient(this)
        //set up toolbar
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)





        mapViewModel =
                ViewModelProviders.of(this, applicationLevelProvider.mapViewModelFactory).get(
                        MapViewModel::class.java
                )

        applicationLevelProvider.appMapViewModel = mapViewModel
        //floating action button, can be removed.
        fab = findViewById(R.id.fab)
        val lambda = { }
        setFabOnclick(lambda)


        setUpNav()


        //check permissions
        initPermissions()

        try {
            // Request location updates
            locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, locationListener)
            Timber.i(" $TAG requesting location")
            val sauce =locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, locationListener)
            CoroutineScope(Dispatchers.IO).launch {
                getLatestLocation()

            }
        } catch (ex: SecurityException) {
            Timber.i("Security Exception, no location available")
        }






    }



    //navigation and interface methods

    fun setFabOnclick(lambda: () -> Unit) {
        fab.setOnClickListener { lambda.invoke() }
    }

    private fun setUpNav() {
        val drawerLayout: DrawerLayout = findViewById(R.id.drawer_layout)
        val navView: NavigationView = findViewById(R.id.nav_view)
        val navController = findNavController(R.id.nav_host_fragment)


        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        appBarConfiguration = AppBarConfiguration(
                setOf(
                        R.id.nav_home, R.id.nav_login_register, R.id.nav_settings,
                        R.id.nav_debug, R.id.nav_share, R.id.nav_send
                ), drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }



//for grabing location
    suspend fun getLatestLocation ():Location? {

        val locale = fusedLocationClient.lastLocation.await()
        applicationLevelProvider.userLocation = locale ?: Location("empty")
        return locale
    }




    //for location component
    private val locationListener: LocationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            Timber.i("location log" + location.longitude + ":" + location.latitude)
            applicationLevelProvider.userLocation = location

        }

        override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }


    fun enableLocationComponent(loadedMapStyle: Style) {
// Check if permissions are enabled and if not let user known
        if (applicationLevelProvider.fineLocationPermission) {

// Create and customize the LocationComponent's options
            val customLocationComponentOptions = LocationComponentOptions.builder(this)
                    .trackingGesturesManagement(true)
                    .accuracyColor(ContextCompat.getColor(this, R.color.colorPrimary))
                    .build()

            val locationComponentActivationOptions =
                    LocationComponentActivationOptions.builder(this, loadedMapStyle)
                            .locationComponentOptions(customLocationComponentOptions)
                            .build()

// Get an instance of the LocationComponent and then adjust its settings
            applicationLevelProvider.mapboxMap.locationComponent.apply {

                // Activate the LocationComponent with options
                activateLocationComponent(locationComponentActivationOptions)

// Enable to make the LocationComponent visible
                isLocationComponentEnabled = true

// Set the LocationComponent's camera mode
                cameraMode = CameraMode.TRACKING

// Set the LocationComponent's render mode
                renderMode = RenderMode.COMPASS
            }
        } else {
            Toast.makeText(this, "Fine Location not enabled", Toast.LENGTH_SHORT).show()
        }
    }

    //permissions methods
    private fun initPermissions() {
        if (!applicationLevelProvider.internetPermission) {
            checkFineLocationPermission()
            checkInternetPermission()
            Timber.i("init - initpermissions")
        }
    }

    private fun checkInternetPermission() {
        Timber.i("init - check internet")
        // Check if the INTERNET permission has been granted
        if (checkSelfPermissionCompat(Manifest.permission.INTERNET) ==
                PackageManager.PERMISSION_GRANTED) {
            Timber.i("init - internet already available")
            // Permission is already available, set boolean in ApplicationLevelProvider
            applicationLevelProvider.internetPermission = true
            //pop snackbar to notify of permissions
            applicationLevelProvider.showSnackbar("Internet permission: ${applicationLevelProvider.internetPermission} \n " +
                    "Fine Location permission: ${applicationLevelProvider.fineLocationPermission}", Snackbar.LENGTH_SHORT)


        } else {
            // Permission is missing and must be requested.
            requestInternetPermission()
        }
    }

    private fun requestInternetPermission() {
        Timber.i("init - request internet")
        // Permission has not been granted and must be requested.
        if (shouldShowRequestPermissionRationaleCompat(Manifest.permission.INTERNET)) {
            // Provide an additional rationale to the user if the permission was not granted
            // and the user would benefit from additional context for the use of the permission.
            // Display a SnackBar with a button to request the missing permission.
            applicationLevelProvider.showSnackbar(
                    "INTERNET acess is required for this app to function at all.",
                    Snackbar.LENGTH_INDEFINITE, "OK"
            ) {
                requestPermissionsCompat(
                        arrayOf(Manifest.permission.INTERNET),
                        MY_PERMISSIONS_REQUEST_INTERNET
                )
            }

        } else {
            applicationLevelProvider.showSnackbar("INTERNET not available", Snackbar.LENGTH_SHORT)

            // Request the permission. The result will be received in onRequestPermissionResult().
            requestPermissionsCompat(arrayOf(Manifest.permission.INTERNET), MY_PERMISSIONS_REQUEST_INTERNET)
        }
    }


    private fun checkFineLocationPermission() {
        Timber.i("init - check fine location")
        // Check if the Camera permission has been granted
        if (checkSelfPermissionCompat(Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED) {
            Timber.i("init -  fine location already granted")
            // Permission is already available, set boolean in ApplicationLevelProvider
            applicationLevelProvider.fineLocationPermission = true
            //pop snackbar to notify of permissions
            applicationLevelProvider.showSnackbar("Internet permission: ${applicationLevelProvider.internetPermission} \n " +
                    "Fine Location permission: ${applicationLevelProvider.fineLocationPermission}", Snackbar.LENGTH_SHORT)

        } else {
            // Permission is missing and must be requested.
            requestFineLocationPermission()
        }
    }


    private fun requestFineLocationPermission() {
        Timber.i("init - request fine location")
        // Permission has not been granted and must be requested.
        if (shouldShowRequestPermissionRationaleCompat(Manifest.permission.ACCESS_FINE_LOCATION)) {
            // Provide an additional rationale to the user if the permission was not granted
            // and the user would benefit from additional context for the use of the permission.
            // Display a SnackBar with a button to request the missing permission.
            applicationLevelProvider.showSnackbar(
                    "GPS location data is needed to provide accurate local results",
                    Snackbar.LENGTH_INDEFINITE, "OK"
            ) {
                requestPermissionsCompat(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                        MY_PERMISSIONS_REQUEST_FINE_LOCATION
                )
            }

        } else {
            applicationLevelProvider.showSnackbar("Fine Location not available", Snackbar.LENGTH_SHORT)

            // Request the permission. The result will be received in onRequestPermissionResult().
            requestPermissionsCompat(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), MY_PERMISSIONS_REQUEST_FINE_LOCATION)
        }
    }


    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<String>, grantResults: IntArray
    ) {
        Timber.i("on request == before while loop permission: ${permissions.toString()} requestcode: $requestCode grantresults: ${grantResults.toString()} ")
        when (requestCode) {
            MY_PERMISSIONS_REQUEST_FINE_LOCATION -> {
                Timber.i("on request == after while loop fine location")
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    applicationLevelProvider.fineLocationPermission = true
                    applicationLevelProvider.showSnackbar("Fine Location granted successfully", Snackbar.LENGTH_SHORT)
                } else {
                    applicationLevelProvider.fineLocationPermission = false
                    applicationLevelProvider.showSnackbar("Fine Location not granted", Snackbar.LENGTH_SHORT)
                }
                return
            }
            MY_PERMISSIONS_REQUEST_INTERNET -> {
                Timber.i("on request == after while loop internet")
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    applicationLevelProvider.internetPermission = true
                    applicationLevelProvider.showSnackbar("Internet granted successfully", Snackbar.LENGTH_SHORT)
                } else {
                    applicationLevelProvider.internetPermission = false
                    applicationLevelProvider.showSnackbar("Internet not granted", Snackbar.LENGTH_SHORT)
                    //
                    TODO("CAUSE APPLICATION TO EXIT HERE")
                }
                return
            }
            // Add other 'when' lines to check for other
            // permissions this app might request.
            else -> {
                Timber.i("on request == after while loop else")
                // Ignore all other requests.
            }
        }
    }


}
/*
*
*
*
*
    var permissionsListener: PermissionsListener = object : PermissionsListener {
        override fun onExplanationNeeded(permissionsToExplain: List<String>) {

        }

        override fun onPermissionResult(granted: Boolean) {
            if (granted) {

                // Permission sensitive logic called here, such as activating the Maps SDK's LocationComponent to show the device's location


            } else {

                // User denied the permission


            }
        }
    }
*
*
*   fun checkPermissions() {
        // Here, this is the current activity
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            != PackageManager.PERMISSION_GRANTED
        ) {

            // Permission is not granted
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            ) {
                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_CONTACTS),
                    MY_PERMISSIONS_REQUEST_FINE_LOCATION
                )

                // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }
        } else {
            // Permission has already been granted
        }

    }
*
*     var permissionsManager = PermissionsManager()

    if (PermissionsManager.areLocationPermissionsGranted(this)) {

// Permission sensitive logic called here, such as activating the Maps SDK's LocationComponent to show the device's location



    } else {
        permissionsManager = PermissionsManager(this)
        permissionsManager.requestLocationPermissions(this)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>,
                                            grantResults: IntArray) {
        permissionsManager.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

}
* */


/*
*
*         /* val fm = supportFragmentManager
             val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
             val fragment2= navHostFragment!!.childFragmentManager.fragments[0]
              println(fragment2.toString())
                      (fragment2 as HomeFragment).mapIT()*/
           // val homeid = findNavController(R.id.nav_host_fragment).currentDestination!!.id
         /*   will give you your current Fragment's id where
*/
          //  private fun navController() = Navigation.findNavController(this, R.id.navHostFragment)
/*            this id is the id which you have given in your Navigation Graph XML file under fragment tag.
            You could also compare currentDestination.label if you wan*/

       /*   *//*  val fragment: HomeFragment =
                fm.findFragmentById(R.id.home) as HomeFragment*//*
         //   NewsItemFragment tag = ( NewsItemFragment)getFragmentManager().findFragmentByTag(MainActivity.NEWSITEM_FRAGMENT)
            val fragment2 = fm.findFragmentById(R.id.nav_host_fragment)
                val fraglist  = fm.fragments
            print(fraglist.toString())
            println("huh")
            println(fragment2)
       *//*  //   println(fragment2!!.tag)
            println(fragment2.id)

            *//*            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()*/
*
* */