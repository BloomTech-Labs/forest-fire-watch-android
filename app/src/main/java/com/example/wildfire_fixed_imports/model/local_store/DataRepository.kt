package com.example.wildfire_fixed_imports.model.local_store


import android.widget.Toast
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.wildfire_fixed_imports.ApplicationLevelProvider
import com.example.wildfire_fixed_imports.DataRepositoryWatcher
import com.example.wildfire_fixed_imports.model.*
import com.example.wildfire_fixed_imports.util.StackTraceInfo
import com.example.wildfire_fixed_imports.util.className
import com.example.wildfire_fixed_imports.util.fileName
import kotlinx.coroutines.*
import timber.log.Timber
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.properties.Delegates

@Suppress("UNCHECKED_CAST")
class DataRepository: DataRepositoryWatcher {
    private val applicationLevelProvider: ApplicationLevelProvider = ApplicationLevelProvider.getApplicaationLevelProviderInstance()
    private val aqidsController = applicationLevelProvider.aqidsController
    private val fireDSController =applicationLevelProvider.fireDSController
    private val mapDrawController = applicationLevelProvider.mapDrawController
    private val nearestNeighborApproach =applicationLevelProvider.experimentalNearestNeighborApproach

    val TAG: String
        get() = "\nclass: $className -- file name: $fileName -- method: ${StackTraceInfo.invokingMethodName} \n"

    var aqiStations: MutableList<AQIStations> by Delegates.observable(
            mutableListOf()
    ){ property, oldValue, newValue ->
        oldValue.addAll(newValue)
        oldValue.toSet()
       aqiGeoJson.postValue(mapDrawController.makeAQIGeoJson(oldValue))
        if (aqiNearestNeighborGeoJson.value.isNullOrEmpty()){
            Timber.d("$TAG \n ${aqiNearestNeighborGeoJson?.value} (aqiNearestNeighborGeoJson.value) isNullORblack")
        getExperimental()
        }
        Timber.d("$TAG \n aqiGeoJson wirrten \n ${aqiGeoJson.value}")
    }
    var fireLocations: MutableList<DSFires> by Delegates.observable(
    mutableListOf()
    ){ property, oldValue, newValue ->
        oldValue.addAll(newValue)
        oldValue.toSet()
        fireGeoJson.postValue( mapDrawController.makeFireGeoJson(oldValue))
        Timber.d("$TAG \n firegeojson wirrten \n ${fireGeoJson.value}")
    }

     var aqiGeoJson:MutableLiveData<String> = MutableLiveData()

     var fireGeoJson:MutableLiveData<String> = MutableLiveData()

    var aqiNearestNeighborGeoJson:MutableLiveData<String>  by Delegates.observable(MutableLiveData()) {property, oldValue, newValue ->
        if (!newValue.value.isNullOrBlank()){
            liveDataLoadingComplete.postValue(true)
        }
    }


    fun initData() {
        CoroutineScope(Dispatchers.IO).launch {
            aqiStations = getAQIstations()?.toMutableList() ?: mutableListOf()
        }
        CoroutineScope(Dispatchers.IO).launch  {
            fireLocations = getFires()?.toMutableList() ?: mutableListOf()
        }
    }

 fun getExperimental() {
     if (aqiNearestNeighborGeoJson.value.isNullOrBlank()) {
         CoroutineScope(Dispatchers.Default).launch {
             aqiNearestNeighborGeoJson.postValue(nearestNeighborApproach.makeGeoJsonCirclesManually(applicationLevelProvider.dataRepository.aqiStations))
         }
     }
}

    suspend fun getFires(): List<DSFires>? {
        val result = fireDSController.getDSFireLocations()
        if (result is SuccessFailWrapper.Success) {
            return result.value ?: listOf()
        } else {
            when (result) {
                is SuccessFailWrapper.Throwable -> Timber.i(result.message)
                is SuccessFailWrapper.Fail -> Timber.i(result.message)
                else -> Timber.i(result.toString())
            }
            return null
        }
    }



    suspend fun getAQIstations(): List<AQIStations>? {
        // 8.0 is max distance that can be set and still expect maximum local resolution (or very close to it), any higher
        // and you will start to notice local stations dropping off the list
        // 15-20 can be a nice middle ground as you'll get your half of north america (West or east coast or central, a bit of central america
        // and a bit of canada -- will lose noticable number of local stations
        // 49-50 will get you from new york to LA but with SIGNIFICANT local resolution loss, I went from 9 stations within 15 miles of me, to 3 stations,
        // however this setting is really nice for doing an overview of the country or similar
        // 80 will cover the hemisphere you're on (roughly),  will lead to massive drop off of local resolution
        //      50 is the current demo setting as it allows us to explore the us but also see how there may be several aqi stations in your city and many in your
        //                  state
        //
        val userLocations:MutableList<WebBELocation?> = applicationLevelProvider.localUser?.mLocations
                ?: mutableListOf<WebBELocation?>()
                        .also{ Timber.e("$TAG mLocations is null, exiting getaqistations unsuccessfully"); return null}

        Timber.e("user locations in get aqi stations" )
        userLocations.forEach {
            Timber.i("$it another test "+it?.latitude)
        }

        val lstOfReturnData = ConcurrentLinkedQueue<SuccessFailWrapper<List<AQIStations>>>()
        userLocations.forEach {
            Timber.i("test 1-${System.currentTimeMillis()}")
            lstOfReturnData.add(
                    aqidsController.getAQIStations(
                            it?.latitude ?: 20.0,
                            it?.longitude ?: 20.0,
                            it?.radius ?: 5.0)
            )
        }
        val compositeResult = mutableListOf<AQIStations>()
        lstOfReturnData.forEach {
            Timber.i("test 2-${System.currentTimeMillis()}")
            if (it is SuccessFailWrapper.Success) {
                //this is what happens when you let the IDE demand shit out of you.
                compositeResult.addAll(it.value ?: listOf())
            } else {
                when (it) {
                    is SuccessFailWrapper.Throwable -> Timber.i("fail at ${it.message} ${it.t.toString()}")
                    is SuccessFailWrapper.Fail -> Timber.i("fail at ${it.message}")
                    else -> Timber.i("fail! $it")
                }

                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(applicationLevelProvider.baseContext, "Some AQI data may be missing, please reload app and try again later", Toast.LENGTH_SHORT).show()
                }
            }
        }
        Timber.e("test + "+compositeResult.toString().subSequence(0,30))
        return cleanAQIStationData(compositeResult)


    }

    fun cleanAQIStationData(aqiStations: List<AQIStations>?) :List<AQIStations> {
        var count = 0
       val set = aqiStations?.toSet()
        val mutListResult =    mutableListOf<AQIStations>()/// arrayOfNulls<AQIStations>(set?.size ?: 1)
        set?.forEach{
            if (!it.aqi.isBlank() && it.aqi.toIntOrNull() != null) {
                mutListResult.add(it)
            }
        }
        return mutListResult.toList()
    }




    companion object {
        private var mInstance: DataRepository? = null
        fun getInstance(): DataRepository {
            if (mInstance == null) mInstance = DataRepository()
            return mInstance as DataRepository
        }
    }


    val liveDataCurrentLoading:MutableLiveData<LoadingDefinition> = MutableLiveData<LoadingDefinition>().apply {
        this.postValue(LoadingDefinition.Throwable())
    }
    val liveDataLoadingComplete:MutableLiveData<Boolean> = MutableLiveData<Boolean>().apply {
        this.postValue(false)
    }
    override fun getCurrentLoading(): LiveData<LoadingDefinition> {

        return liveDataCurrentLoading
    }

    override fun loadingComplete(): LiveData<Boolean> {
        if (!aqiNearestNeighborGeoJson.value.isNullOrEmpty()) {
            liveDataLoadingComplete.postValue(true)
        }
        return liveDataLoadingComplete
    }
}