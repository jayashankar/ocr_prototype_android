package com.example.ocrprototypeapp;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;

import com.example.ocrprototypeapp.R;

import androidx.fragment.app.DialogFragment;

public class ListDialogFragment extends DialogFragment {

    public void setListener(DialogClickListener listener) {
        this.listener = listener;
    }

    private DialogClickListener listener;

    public interface DialogClickListener {
        void didSelectOption(Integer buttonIndex);
    }


    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        String[] listOptions = { "Take Photo", "Choose From Gallery", "Cancel" };

        builder.setTitle(R.string.add_photo)
                .setItems( listOptions, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {

                        if (listener != null) {
                            listener.didSelectOption(which);
                        }

                    }
                });
        return builder.create();
    }


}
