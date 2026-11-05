package sk.menureader

import android.app.Application
import sk.menureader.data.AppDatabase
import sk.menureader.data.DisplayStore
import sk.menureader.data.ProfileStore
import sk.menureader.data.Repository

class MenuReaderApp : Application() {
    lateinit var repository: Repository
        private set
    lateinit var profiles: ProfileStore
        private set
    lateinit var display: DisplayStore
        private set

    override fun onCreate() {
        super.onCreate()
        repository = Repository(this, AppDatabase.create(this))
        profiles = ProfileStore(this)
        display = DisplayStore(this)
    }
}
