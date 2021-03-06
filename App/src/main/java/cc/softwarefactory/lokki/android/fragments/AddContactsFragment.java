/*
Copyright (c) 2014-2015 F-Secure
See LICENSE for details
*/
package cc.softwarefactory.lokki.android.fragments;

import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.androidquery.AQuery;
import com.androidquery.callback.AjaxCallback;
import com.androidquery.callback.AjaxStatus;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

import cc.softwarefactory.lokki.android.MainApplication;
import cc.softwarefactory.lokki.android.R;
import cc.softwarefactory.lokki.android.avatar.AvatarLoader;
import cc.softwarefactory.lokki.android.datasources.contacts.ContactDataSource;
import cc.softwarefactory.lokki.android.datasources.contacts.DefaultContactDataSource;
import cc.softwarefactory.lokki.android.models.Contact;
import cc.softwarefactory.lokki.android.androidServices.DataService;
import cc.softwarefactory.lokki.android.utilities.AnalyticsUtils;
import cc.softwarefactory.lokki.android.utilities.ContactUtils;
import cc.softwarefactory.lokki.android.utilities.PreferenceUtils;
import cc.softwarefactory.lokki.android.utilities.ServerApi;


public class AddContactsFragment extends Fragment {

    private static final String TAG = "AddContacts";
    private ContactDataSource mContactDataSource;
    private ArrayList<String> contactList;
    private AQuery aq;
    private Boolean cancelAsynTasks = false;
    private Context context;
    private AvatarLoader avatarLoader;
    private EditText inputSearch;
    private Button clearFilter;
    private ArrayAdapter<String> adapter;
    private TextView noContactsMessage;

    public AddContactsFragment() {
        contactList = new ArrayList<>();
        mContactDataSource = new DefaultContactDataSource();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.activity_add_contacts, container, false);
        aq = new AQuery(getActivity(), rootView);
        cancelAsynTasks = false;
        context = getActivity().getApplicationContext();
        avatarLoader = new AvatarLoader(context);
        inputSearch = (EditText) rootView.findViewById(R.id.add_contact_search);
        inputSearch.setEnabled(false);
        inputSearch.setAlpha(0);
        noContactsMessage = (TextView) rootView.findViewById(R.id.no_contacts_message);
        noContactsMessage.setText(R.string.no_contacts_to_show);
        clearFilter = (Button) rootView.findViewById(R.id.clear_filter);

        return rootView;
    }

    @Override
    public void onResume() {
        super.onResume();
        ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(R.string.add_contacts);
        }
        loadContacts();
        enableSearchFilter();
        AnalyticsUtils.screenHit(getString(R.string.analytics_screen_add_contacts));
    }

    private void loadContacts() {
        String[] loadingList = {getString(R.string.loading)};
        aq.id(R.id.add_contacts_list_view).adapter(new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1, loadingList));
        new getAllEmailAddressesAsync().execute();
    }

    private void enableSearchFilter() {

        inputSearch.addTextChangedListener(new TextWatcher() {

            @Override
            public void onTextChanged(CharSequence cs, int arg1, int arg2, int arg3) {
                // When user changed the Text
                if (adapter != null) {
                    adapter.getFilter().filter(cs);
                }
                clearFilter.setVisibility(cs.length() == 0 ? View.INVISIBLE : View.VISIBLE);
            }

            @Override
            public void beforeTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {
                // TODO Auto-generated method stub

            }

            @Override
            public void afterTextChanged(Editable arg0) {
                // TODO Auto-generated method stub
            }
        });

        inputSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AnalyticsUtils.eventHit(getString(R.string.analytics_category_ux),
                        getString(R.string.analytics_action_click),
                        getString(R.string.analytics_label_search_contacts_textbox));
            }
        });

        clearFilter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AnalyticsUtils.eventHit(getString(R.string.analytics_category_ux),
                        getString(R.string.analytics_action_click),
                        getString(R.string.analytics_label_clear_search_contacts_textbox_button));
                inputSearch.setText("");
            }
        });
    }


    public void setContactUtils(ContactDataSource contactDataSource) {
        this.mContactDataSource = contactDataSource;
    }

    private class prepareAdapterAsync extends AsyncTask<Void, Void, Boolean> {

        @Override
        protected Boolean doInBackground(Void... params) {

            Log.d(TAG, "prepareAdapterAsync");
            getContactList();
            return true;
        }

        @Override
        protected void onPostExecute(Boolean contactsExist) {

            if (isAdded()) {
                Log.d(TAG, "prepareAdapterAsync - ContactList: " + contactList);
                setListAdapter();
            }
            super.onPostExecute(contactsExist);
        }
    }

    private class getAllEmailAddressesAsync extends AsyncTask<Void, Void, MainApplication.Contacts> {

        @Override
        protected MainApplication.Contacts doInBackground(Void... params) {
            return mContactDataSource.getContacts(context);
        }

        @Override
        protected void onPostExecute(MainApplication.Contacts contactsResult) {

            if (contactsResult == null || !isAdded() || cancelAsynTasks) {
                super.onPostExecute(MainApplication.contacts);
                return;
            }
            Log.d(TAG, "Number of contacts: " + (contactsResult.size() - 1));
            Log.d(TAG, "Contacts: " + contactsResult);

            // If contacts don't exist already, use data source result
            if (MainApplication.contacts == null){
                MainApplication.contacts = contactsResult;
            } else {
                // If contacts already exist, combine data source result with existing data
                for (Map.Entry<String, Contact> contactEntry : contactsResult.entrySet()) {
                    String email = contactEntry.getKey();
                    if (!MainApplication.contacts.hasEmail(email)) {
                        Contact contact = contactsResult.getContactByEmail(email);
                        MainApplication.contacts.put(email, contact);
                    }
                }
            }

            //Synchronize mapping JSON with contacts
            try {
                PreferenceUtils.setString(context, PreferenceUtils.KEY_CONTACTS, MainApplication.contacts.serialize());
            } catch (IOException e) {
                Log.e(TAG, "Exception while serializing contacts results to JSON: " + e.getMessage());
            }
            new prepareAdapterAsync().execute();
            super.onPostExecute(MainApplication.contacts);
        }
    }

    private void getContactList() {

        contactList = new ArrayList<>();

        Map<String, Contact> contacts = MainApplication.contacts;

        if (contacts == null)
            return;

        for (Map.Entry<String, Contact> contactEntry : contacts.entrySet()) {
            String email = contactEntry.getKey();
            Contact contact = contactEntry.getValue();

            if (alreadyAdded(email))
                continue;
            contactList.add(contact.getName());
        }
        Collections.sort(contactList);
        Log.d(TAG, "Adapter ContactList: " + contactList);
    }

    private boolean alreadyAdded(String email) {

        if (MainApplication.dashboard == null) {
            return false;
        }
        return MainApplication.dashboard.containsEmail(email);
    }

    private void setListAdapter() {

        //TODO: array adapter should be a list of Contacts instead of names. Current implementation will cause bugs if there are contacts with same name.
        adapter = new ArrayAdapter<String>(getActivity(), R.layout.add_people_row_layout, contactList) {

            @Override
            public View getView(final int position, View convertView, ViewGroup parent) {

                ViewHolder holder;

                if (convertView == null) {
                    convertView = getActivity().getLayoutInflater().inflate(R.layout.add_people_row_layout, parent, false);
                    holder = new ViewHolder();
                    holder.name = (TextView) convertView.findViewById(R.id.contact_name);
                    holder.email = (TextView) convertView.findViewById(R.id.contact_email);
                    holder.photo = (ImageView) convertView.findViewById(R.id.contact_photo);
                    convertView.setTag(holder);

                } else {
                    holder = (ViewHolder) convertView.getTag();
                }

                AQuery aq = new AQuery(convertView);
                String contactName = getItem(position);
                final String email = MainApplication.contacts.getEmailByName(contactName);

                inputSearch.setEnabled(true);
                inputSearch.setAlpha(1);
                noContactsMessage.setAlpha(0);

                avatarLoader.load(email, holder.photo);

                aq.id(holder.name).text(contactName);
                aq.id(holder.email).text(email);
                holder.position = position;

                convertView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        AnalyticsUtils.eventHit(getString(R.string.analytics_category_ux),
                                getString(R.string.analytics_action_click),
                                getString(R.string.analytics_label_contact_in_list));
                        final Context context = getContext();
                        String title = getString(R.string.add_contact);
                        String message = getString(R.string.add_contact_dialog_save, email);
                        new AlertDialog.Builder(context)
                                .setTitle(title)
                                .setMessage(message)
                                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        AnalyticsUtils.eventHit(getString(R.string.analytics_category_ux),
                                                getString(R.string.analytics_action_click),
                                                getString(R.string.analytics_label_confirm_contact_add_from_list_dialog));

                                        if(ContactUtils.isSelf(context, email)) {
                                            Toast.makeText(context, R.string.cant_add_self_as_contact, Toast.LENGTH_LONG).show();
                                        } else {
                                            ServerApi.allowPeople(context, email, new AjaxCallback<String>() {
                                                                              @Override
                                                                              public void callback(String url, String result, AjaxStatus status)  {
                                                    ServerApi.logStatus("allowPeople", status);
                                                    if(status.getError() != null)
                                                        Toast.makeText(context, R.string.unable_to_add_contact, Toast.LENGTH_LONG).show();
                                                    else {
                                                        ContactUtils.addLocalContact(context, email);
                                                        contactList.remove(position);
                                                        notifyDataSetChanged();
                                                        DataService.getDashboard(context);
                                                        Toast.makeText(context, R.string.contact_added, Toast.LENGTH_SHORT).show();
                                                    }
                                            }
                                        });
                                        }
                                    }
                                })
                                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                                                         @Override
                                                         public void onClick(DialogInterface dialog, int which) {
                                        AnalyticsUtils.eventHit(getString(R.string.analytics_category_ux),
                                                getString(R.string.analytics_action_click),
                                                getString(R.string.analytics_label_cancel_contact_add_from_list_dialog));

                                }
                            })
                                .show();
                    }
                });

                return convertView;
            }
        };

        aq.id(R.id.add_contacts_list_view).adapter(adapter);
    }

    public static void addContactFromEmail(final Context context) {

        final EditText input = new EditText(context); // Set an EditText view to get user input
        input.setSingleLine(true);
        input.setHint(R.string.contact_email_address);
        input.setInputType(InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS);

        final AlertDialog addContactDialog = new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.add_contact))
                .setView(input)
                .setPositiveButton(R.string.ok, null)
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        AnalyticsUtils.eventHit(context.getString(R.string.analytics_category_ux),
                                context.getString(R.string.analytics_action_click),
                                context.getString(R.string.analytics_label_cancel_contact_add_from_email_dialog));
                    }
                })
                .create();

        addContactDialog.setOnShowListener(new DialogInterface.OnShowListener() {

            @Override
            public void onShow(DialogInterface dialog) {
                addContactDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {

                    @Override
                    public void onClick(View view) {
                        Editable value = input.getText();
                        if (value == null || value.toString().isEmpty()) {
                            input.setError(context.getResources().getString(R.string.required));
                            return;
                        }

                        AnalyticsUtils.eventHit(context.getString(R.string.analytics_category_ux),
                                context.getString(R.string.analytics_action_click),
                                context.getString(R.string.analytics_label_confirm_contact_add_from_email_dialog_successful));
                        final String email = value.toString();

                        if(ContactUtils.isSelf(context, email)) {
                            Toast.makeText(context, R.string.cant_add_self_as_contact, Toast.LENGTH_LONG).show();
                        } else {
                            ServerApi.allowPeople(context, email, new AjaxCallback<String>() {
                                @Override
                                public void callback(String url, String result, AjaxStatus status)  {
                                    ServerApi.logStatus("allowPeople", status);
                                    if(status.getError() != null)
                                        Toast.makeText(context, R.string.unable_to_add_contact, Toast.LENGTH_LONG).show();
                                    else {
                                        ContactUtils.addLocalContact(context, email);
                                        DataService.getDashboard(context);
                                        Toast.makeText(context, R.string.contact_added, Toast.LENGTH_SHORT).show();
                                    }
                                }
                            });
                        }
                        addContactDialog.dismiss();
                    }
                });
            }
        });

        addContactDialog.show();
    }

    static class ViewHolder {
        TextView name;
        TextView email;
        ImageView photo;
        int position;
    }


}
