package sk.menureader

import android.app.Application
import sk.menureader.cloud.CloudSettings
import sk.menureader.data.AppDatabase
import sk.menureader.data.ProfileStore
import sk.menureader.data.Repository
import sk.menureader.data.SettingsStore

class MenuReaderApp : Application() {
    lateinit var repository: Repository
        private set
    lateinit var profiles: ProfileStore
        private set
    lateinit var settings: SettingsStore
        private set
    lateinit var cloud: CloudSettings
        private set

    override fun onCreate() {
        super.onCreate()
        repository = Repository(this, AppDatabase.create(this))
        profiles = ProfileStore(this)
        settings = SettingsStore(this)
        cloud = CloudSettings(this)
    }
}
