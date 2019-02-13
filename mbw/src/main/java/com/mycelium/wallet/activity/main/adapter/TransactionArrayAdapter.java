package com.mycelium.wallet.activity.main.adapter;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.mrd.bitlib.model.Address;
import com.mycelium.wallet.MbwManager;
import com.mycelium.wallet.R;
import com.mycelium.wallet.Utils;
import com.mycelium.wallet.activity.util.AdaptiveDateFormat;
import com.mycelium.wallet.activity.util.TransactionConfirmationsDisplay;
import com.mycelium.wallet.activity.util.ValueExtensionsKt;
import com.mycelium.wallet.persistence.MetadataStorage;
import com.mycelium.wapi.wallet.GenericTransaction;
import com.mycelium.wapi.wallet.coins.GenericAssetInfo;
import com.mycelium.wapi.wallet.coins.Value;
import com.mycelium.wapi.wallet.fiat.coins.FiatType;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static android.content.Context.MODE_PRIVATE;
import static com.mycelium.wallet.activity.send.SendMainActivity.TRANSACTION_FIAT_VALUE;
import static com.mycelium.wallet.external.changelly.bch.ExchangeFragment.BCH_EXCHANGE;
import static com.mycelium.wallet.external.changelly.bch.ExchangeFragment.BCH_EXCHANGE_TRANSACTIONS;

public class TransactionArrayAdapter extends ArrayAdapter<GenericTransaction> {
   private final MetadataStorage _storage;
   protected Context _context;
   private DateFormat _dateFormat;
   private MbwManager _mbwManager;
   private Fragment _containerFragment;
   private SharedPreferences transactionFiatValuePref;
   private Set<String> exchangeTransactions;

   public TransactionArrayAdapter(Context context, List<GenericTransaction> transactions, Map<Address, String> addressBook) {
      this(context, transactions, null, addressBook, true);
   }

   public TransactionArrayAdapter(Context context,
                                  List<GenericTransaction> transactions,
                                  Fragment containerFragment,
                                  Map<Address, String> addressBook,
                                  boolean alwaysShowAddress) {
      super(context, R.layout.transaction_row, transactions);
      _context = context;
      _dateFormat = new AdaptiveDateFormat(context);
      _mbwManager = MbwManager.getInstance(context);
      _containerFragment = containerFragment;
      _storage = _mbwManager.getMetadataStorage();

      transactionFiatValuePref = context.getSharedPreferences(TRANSACTION_FIAT_VALUE, MODE_PRIVATE);

      SharedPreferences sharedPreferences = context.getSharedPreferences(BCH_EXCHANGE, MODE_PRIVATE);
      exchangeTransactions = sharedPreferences.getStringSet(BCH_EXCHANGE_TRANSACTIONS, new HashSet<String>());
   }

   @NonNull
   @Override
   public View getView(final int position, View convertView, ViewGroup parent) {
      // Only inflate a new view if we are not reusing an old one
      View rowView = convertView;
      if (rowView == null) {
         LayoutInflater inflater = (LayoutInflater) _context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
         rowView = Preconditions.checkNotNull(inflater.inflate(R.layout.transaction_row, parent, false));
      }

      // Make sure we are still added
      if (_containerFragment != null && !_containerFragment.isAdded()) {
         // We have observed that the fragment can be disconnected at this
         // point
         return rowView;
      }

      final GenericTransaction record = getItem(position);

      // Determine Color
      int color;
      if (record.isIncoming()) {
         color = _context.getResources().getColor(R.color.green);
      } else {
         color = _context.getResources().getColor(R.color.red);
      }

      // Set Date
      Date date = new Date(record.getTimestamp() * 1000L);
      TextView tvDate = rowView.findViewById(R.id.tvDate);
      tvDate.setText(_dateFormat.format(date));

      // Set value
      TextView tvAmount = rowView.findViewById(R.id.tvAmount);
      tvAmount.setText(ValueExtensionsKt.toStringWithUnit(record.getTransferred().abs(), _mbwManager.getBitcoinDenomination()));
      tvAmount.setTextColor(color);

      // Set alternative value
      TextView tvFiat = rowView.findViewById(R.id.tvFiatAmount);
      GenericAssetInfo alternativeCurrency = _mbwManager.getCurrencySwitcher().getCurrentFiatCurrency();

      if (alternativeCurrency != null) {
         Value recordValue = record.getTransferred().abs();
         Value alternativeValue = _mbwManager.getExchangeRateManager().get(recordValue, alternativeCurrency);

         if (alternativeValue == null) {
            tvFiat.setVisibility(View.GONE);
         } else {
            tvFiat.setVisibility(View.VISIBLE);
            tvFiat.setText(ValueExtensionsKt.toStringWithUnit(alternativeValue, _mbwManager.getBitcoinDenomination()));
            tvFiat.setTextColor(color);
         }
      } else {
         tvFiat.setVisibility(View.GONE);
      }

      TextView tvFiatTimed = rowView.findViewById(R.id.tvFiatAmountTimed);
      String value = transactionFiatValuePref.getString(record.getId().toHex(), null);
      tvFiatTimed.setVisibility(value != null ? View.VISIBLE : View.GONE);
      if(value != null) {
         tvFiatTimed.setText(value);
      }

      // Show confirmations indicator
      int confirmations = record.getConfirmations();
      TransactionConfirmationsDisplay tcdConfirmations = rowView.findViewById(R.id.tcdConfirmations);
      if (record.isQueuedOutgoing()) {
         // Outgoing, not broadcasted
         tcdConfirmations.setNeedsBroadcast();
      } else {
         tcdConfirmations.setConfirmations(confirmations);
      }

      // Show label or confirmations
      TextView tvLabel = (TextView) rowView.findViewById(R.id.tvTransactionLabel);
      String label = _storage.getLabelByTransaction(record.getId());
      if (label.length() == 0) {
         // if we have no txLabel show the confirmation state instead - to keep they layout ballanced
         String confirmationsText;
         if (record.isQueuedOutgoing()) {
            confirmationsText = _context.getResources().getString(R.string.transaction_not_broadcasted_info);
         } else {
            if (confirmations > 6) {
               confirmationsText = _context.getResources().getString(R.string.confirmed);
            } else {
               confirmationsText = _context.getResources().getString(R.string.confirmations, confirmations);
            }
         }
         tvLabel.setText(confirmationsText);
      } else {
         tvLabel.setText(label);
      }

      // Show risky unconfirmed warning if necessary
      TextView tvWarnings = rowView.findViewById(R.id.tvUnconfirmedWarning);
      if (confirmations <= 0) {
         ArrayList<String> warnings = new ArrayList<String>();
         if (record.getConfirmationRiskProfile().isPresent()) {
            if (record.getConfirmationRiskProfile().get().hasRbfRisk) {
               warnings.add(_context.getResources().getString(R.string.warning_reason_rbf));
            }
            if (record.getConfirmationRiskProfile().get().unconfirmedChainLength > 0) {
               warnings.add(_context.getResources().getString(R.string.warning_reason_unconfirmed_parent));
            }
            if (record.getConfirmationRiskProfile().get().isDoubleSpend) {
               warnings.add(_context.getResources().getString(R.string.warning_reason_doublespend));
            }
         }

         if (warnings.size() > 0) {
            tvWarnings.setText(_context.getResources().getString(R.string.warning_risky_unconfirmed, Joiner.on(", ").join(warnings)));
            tvWarnings.setVisibility(View.VISIBLE);
         } else {
            tvWarnings.setVisibility(View.GONE);
         }
      } else {
         tvWarnings.setVisibility(View.GONE);
      }

      rowView.setTag(record);
      return rowView;
   }
}
