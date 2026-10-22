package br.italo.italock

import android.app.Application
import android.content.Context
import br.italo.italock.i18n.L10n

/** Só prepara o idioma do app antes de qualquer tela, serviço ou receptor rodar. */
class ItalockApp : Application() {
    override fun attachBaseContext(base: Context) {
        L10n.init(base)
        super.attachBaseContext(L10n.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        L10n.init(this)
        L10n.syncWithSystem(this)
    }
}
