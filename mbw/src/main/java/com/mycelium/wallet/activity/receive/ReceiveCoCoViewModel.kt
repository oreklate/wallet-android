package com.mycelium.wallet.activity.receive

import android.app.Application
import com.mycelium.wallet.R
import com.mycelium.wallet.Utils
import com.mycelium.wapi.wallet.WalletAccount
import com.mycelium.wapi.wallet.colu.ColuPubOnlyAccount
import com.mycelium.wapi.wallet.currency.CurrencyValue

class ReceiveCoCoViewModel(application: Application) : ReceiveCoinsViewModel(application) {
    private lateinit var accountLabel: String

    override fun init(account: WalletAccount<*,*>, hasPrivateKey: Boolean, showIncomingUtxo: Boolean) {
        super.init(account, hasPrivateKey, showIncomingUtxo)
        accountLabel = (account as ColuPubOnlyAccount).coinType.symbol
        model = ReceiveCoinsModel(getApplication(), account, accountLabel, hasPrivateKey, showIncomingUtxo)
    }

    override fun getHint() = context.getString(R.string.amount_hint_denomination, account.coinType.symbol)

    override fun getFormattedValue(sum: CurrencyValue) = Utils.getColuFormattedValueWithUnit(sum)

    override fun getCurrencyName() = account.coinType.symbol

    override fun getTitle(): String {
        return if (CurrencyValue.isNullOrZero(model.amountData.value)) {
            context.getString(R.string.address_title, accountLabel)
        } else {
            context.getString(R.string.payment_request)
        }
    }


}