/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.contacts.editor;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnShowListener;
import android.os.Bundle;
import android.os.Handler;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextUtils.TruncateAt;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import com.android.contacts.prize.PrizeToastUtils;//prize-add for dido os 8.0-hpf-2017-7-19
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.android.contacts.R;
import com.android.contacts.common.model.RawContactDelta;
import com.android.contacts.common.ContactsUtils;
import com.android.contacts.common.model.ValuesDelta;
import com.android.contacts.common.model.RawContactModifier;
import com.android.contacts.common.model.account.AccountType.EditType;
import com.android.contacts.common.model.dataitem.DataKind;
import com.android.contacts.editor.TextFieldsEditorView;//prize-add for dido os 8.0-hpf-2017-7-19
import com.android.contacts.util.DialogManager;
import com.android.contacts.util.DialogManager.DialogShowingView;

import com.mediatek.contacts.GlobalEnv;
import com.mediatek.contacts.util.Log;

import java.util.List;

/**
 * Base class for editors that handles labels and values. Uses
 * {@link ValuesDelta} to read any existing {@link RawContact} values, and to
 * correctly write any changes values.
 */
public abstract class LabeledEditorView extends LinearLayout implements Editor, DialogShowingView {
    protected static final String DIALOG_ID_KEY = "dialog_id";
    private static final int DIALOG_ID_CUSTOM = 1;

    private static final int INPUT_TYPE_CUSTOM = EditorInfo.TYPE_CLASS_TEXT
            | EditorInfo.TYPE_TEXT_FLAG_CAP_WORDS;

    private Spinner mLabel;
    private EditTypeAdapter mEditTypeAdapter;
    private View mDeleteContainer;
    private ImageView mDelete;

    private DataKind mKind;
    private ValuesDelta mEntry;
    private RawContactDelta mState;
    private boolean mReadOnly;
    private boolean mWasEmpty = true;
    private boolean mIsDeletable = true;
    private boolean mIsAttachedToWindow;

    private EditType mType;

    private ViewIdGenerator mViewIdGenerator;
    private DialogManager mDialogManager = null;
    private EditorListener mListener;
    protected int mMinLineItemHeight;
    /*prize-add for dido os 8.0-hpf-2017-7-19-start*/
    private View mDivider;
    private View mEditorDivider;
    private ImageView mPrizeFieldsEditorBtn;
    private View mPrizeFieldsEditorBtnContainer;
    private Context mContext;
    private boolean mIsSaveInSIM = false;
    private boolean mIsEmailItem = false;
    /*prize-add for dido os 8.0-hpf-2017-7-19-end*/

    /**
     * A marker in the spinner adapter of the currently selected custom type.
     */
    public static final EditType CUSTOM_SELECTION = new EditType(0, 0);

    private OnItemSelectedListener mSpinnerListener = new OnItemSelectedListener() {

        @Override
        public void onItemSelected(
                AdapterView<?> parent, View view, int position, long id) {
            onTypeSelectionChange(position);
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
        }
    };

    public LabeledEditorView(Context context) {
        super(context);
        init(context);
    }

    public LabeledEditorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
        this.mContext = context;//prize-add for dido os 8.0-hpf-2017-7-19
    }

    public LabeledEditorView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    public Long getRawContactId() {
        return mState == null ? null : mState.getRawContactId();
    }

    private void init(Context context) {
        mMinLineItemHeight = context.getResources().getDimensionPixelSize(
                R.dimen.editor_min_line_item_height);
    }

    /** {@inheritDoc} */
    @Override
    protected void onFinishInflate() {
        mLabel = (Spinner) findViewById(R.id.spinner);
        mLabel.setBackgroundResource(R.drawable.prize_selector_spinner_arrow);//prize-add-huangpengfei-2016-11-2
        // Turn off the Spinner's own state management. We do this ourselves on rotation
        mLabel.setId(View.NO_ID);
        mLabel.setOnItemSelectedListener(mSpinnerListener);
        ViewSelectedFilter.suppressViewSelectedEvent(mLabel);
        mLabel.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (v == mLabel) {
                    final InputMethodManager inputMethodManager = (InputMethodManager)
                            getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                    inputMethodManager.hideSoftInputFromWindow(
                            mLabel.getWindowToken(), /* flags */ 0);
                }
                return false;
            }
        });


        mDelete = (ImageView) findViewById(R.id.delete_button);
        mDeleteContainer = findViewById(R.id.delete_button_container);
        mDeleteContainer.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // defer removal of this button so that the pressed state is visible shortly
                new Handler().post(new Runnable() {
                    @Override
                    public void run() {
                        // Don't do anything if the view is no longer attached to the window
                        // (This check is needed because when this {@link Runnable} is executed,
                        // we can't guarantee the view is still valid.
                        if (!mIsAttachedToWindow) {
                            return;
                        }
                        // Send the delete request to the listener (which will in turn call
                        // deleteEditor() on this view if the deletion is valid - i.e. this is not
                        // the last {@link Editor} in the section).
                        if (mListener != null) {
                            mListener.onDeleteRequested(LabeledEditorView.this);
                        }
                    }
                });
            }
        });
        /*prize-add for dido os 8.0-hpf-2017-7-19-start*/
        mDivider = findViewById(R.id.prize_divider);
        mEditorDivider = findViewById(R.id.prize_editor_divider);
        mPrizeFieldsEditorBtn = (ImageView)findViewById(R.id.prize_fields_editor_btn);
        mPrizeFieldsEditorBtnContainer = findViewById(R.id.prize_fields_editor_btn_container);
        if(mPrizeFieldsEditorBtnContainer != null){
	        mPrizeFieldsEditorBtnContainer.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					if(mIsShowAddFieldBtn){
						Log.d(TAG,"[onClick]   add item...");
						if(mIsSaveInSIM){
							if(mIsEmailItem){
								PrizeToastUtils.showToast(mContext, R.string.prize_add_email_invalid_tips, 0);
							}else{
								PrizeToastUtils.showToast(mContext, R.string.prize_add_field_btn_invalid_tips, 0);
							}
							return;
						}
						mOnAddItemRequestListener.onAddItemRequest();
					}else{
						Log.d(TAG,"[onClick]   remove item...");
		                new Handler().post(new Runnable() {
		                    @Override
		                    public void run() {
		                        if (!mIsAttachedToWindow) {
		                            return;
		                        }
		                        if (mListener != null) {
		                            mListener.onDeleteRequested(LabeledEditorView.this);
		                            ((com.android.contacts.activities.ContactEditorActivity)mContext).onChange();//prize-add-hpf-2017-12-4
		                        }
		                    }
		                });
					}
				}
			});
        }
        /*prize-add for dido os 8.0-hpf-2017-7-19-end*/
        /*prize-remove huangliemin-2016-5-31 start*/
        /*
        setPadding(getPaddingLeft(), getPaddingTop(), getPaddingRight(),
                (int) getResources().getDimension(R.dimen.editor_padding_between_editor_views));
        */
        /*prize-remove huangliemin-2016-5-31 end*/
    }

    /*prize-add for dido os 8.0-hpf-2017-7-19-start*/
    private boolean mIsShowAddFieldBtn = true;
    private OnAddItemRequestListener mOnAddItemRequestListener;
    public void setPrizeFieldsEditorBtnEnable(boolean enable){
    	if(mPrizeFieldsEditorBtn != null){
    		if(enable){
    			mPrizeFieldsEditorBtn.setVisibility(View.VISIBLE);
    		}else{
    			mPrizeFieldsEditorBtn.setVisibility(View.GONE);
    		}
    	}
    }
    
    public void showAddFieldsBtn(){
    	if(mPrizeFieldsEditorBtn != null){
    		mPrizeFieldsEditorBtn.setImageDrawable(
    				mContext.getResources().getDrawable(R.drawable.prize_selector_add_text_field_item));
    		mIsShowAddFieldBtn = true;
    	}
    }
    
    public void showRemoveFieldsBtn(){
    	if(mPrizeFieldsEditorBtn != null){
    		mPrizeFieldsEditorBtn.setImageDrawable(
    				mContext.getResources().getDrawable(R.drawable.prize_selector_remove_text_field_item));
    		mIsShowAddFieldBtn = false;
    	}
    }
    
    public void setOnAddItemRequestListener(OnAddItemRequestListener onAddItemRequestListener){
    	mOnAddItemRequestListener = onAddItemRequestListener;
    }
    
    public interface OnAddItemRequestListener{
    	void onAddItemRequest();
    }
    
    public void setDividerVisibility(boolean visibility){
    	Log.d(TAG,"[setDividerVisibility] visibility = "+visibility);
    	if(mDivider != null){
    		mDivider.setVisibility(visibility?View.VISIBLE:View.GONE);
    	}
    }
    
    public void setEditorDividerVisibility(boolean visibility){
    	Log.d(TAG,"[setEditorDividerVisibility] visibility = "+visibility);
    	if(mEditorDivider != null){
    		mEditorDivider.setVisibility(visibility?View.VISIBLE:View.GONE);
    	}
    }
    
    /*prize-add for dido os 8.0-hpf-2017-7-19-end*/
    
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // Keep track of when the view is attached or detached from the window, so we know it's
        // safe to remove views (in case the user requests to delete this editor).
        mIsAttachedToWindow = true;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mIsAttachedToWindow = false;
    }

    @Override
    public void markDeleted() {
        // Keep around in model, but mark as deleted
        mEntry.markDeleted();
    }

    @Override
    public void deleteEditor() {
        markDeleted();

        // Remove the view
        EditorAnimator.getInstance().removeEditorView(this);
    }

    public boolean isReadOnly() {
        return mReadOnly;
    }

    public int getBaseline(int row) {
        if (row == 0 && mLabel != null) {
            return mLabel.getBaseline();
        }
        return -1;
    }

    /**
     * Configures the visibility of the type label button and enables or disables it properly.
     */
    private void setupLabelButton(boolean shouldExist) {
    	Log.d(TAG,"[setupLabelButton]   shouldExist = "+shouldExist);
        if (shouldExist) {
            mLabel.setEnabled(!mReadOnly && isEnabled());
            mLabel.setVisibility(View.VISIBLE);
        } else {
            mLabel.setVisibility(View.GONE);
        }
    }

    /**
     * Configures the visibility of the "delete" button and enables or disables it properly.
     */
    private void setupDeleteButton() {
        if (mIsDeletable) {
            mDeleteContainer.setVisibility(View.VISIBLE);
            mDelete.setEnabled(!mReadOnly && isEnabled());
        } else {
            mDeleteContainer.setVisibility(View.GONE);
        }
    }

    public void setDeleteButtonVisible(boolean visible) {
        if (mIsDeletable) {
            mDeleteContainer.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
        }
    }

    protected void onOptionalFieldVisibilityChange() {
    	Log.d(TAG, "[onOptionalFieldVisibilityChange]");
    	
        if (mListener != null) {
            mListener.onRequest(EditorListener.EDITOR_FORM_CHANGED);
        }
    }

    @Override
    public void setEditorListener(EditorListener listener) {
        mListener = listener;
    }

    protected EditorListener getEditorListener(){
        return mListener;
    }

    @Override
    public void setDeletable(boolean deletable) {
        mIsDeletable = deletable;
        setupDeleteButton();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        mLabel.setEnabled(!mReadOnly && enabled);
        mDelete.setEnabled(!mReadOnly && enabled);
    }

    public Spinner getLabel() {
        return mLabel;
    }

    public ImageView getDelete() {
        return mDelete;
    }

    protected DataKind getKind() {
        return mKind;
    }

    protected ValuesDelta getEntry() {
        return mEntry;
    }

    protected EditType getType() {
        return mType;
    }

    /**
     * Build the current label state based on selected {@link EditType} and
     * possible custom label string.
     */
    public void rebuildLabel() {
        mEditTypeAdapter = new EditTypeAdapter(getContext());
        mLabel.setAdapter(mEditTypeAdapter);
        Log.d(TAG, "[rebuildLabel] hasCustomSelection(): " + mEditTypeAdapter.hasCustomSelection());
        if (mEditTypeAdapter.hasCustomSelection()) {
            mLabel.setSelection(mEditTypeAdapter.getPosition(CUSTOM_SELECTION));
            mDeleteContainer.setContentDescription(
                    getContext().getString(R.string.editor_delete_view_description,
                            mEntry.getAsString(mType.customColumn),
                            getContext().getString(mKind.titleRes)));
        } else {
            mLabel.setSelection(mEditTypeAdapter.getPosition(mType));
            if (mType != null) {
                mDeleteContainer.setContentDescription(
                        getContext().getString(R.string.editor_delete_view_description,
                                getContext().getString(mType.labelRes),
                                getContext().getString(mKind.titleRes)));
            } else {
                mDeleteContainer.setContentDescription(
                        getContext().getString(R.string.editor_delete_view_description_short,
                                getContext().getString(mKind.titleRes)));
            }
            /* M: add for aas @{*/
            Log.d(TAG, "[rebuildLabel] Position: " + mEditTypeAdapter.getPosition(mType) +
                    ",mType: " + mType);
            GlobalEnv.getAasExtension().rebuildLabelSelection(mState, mLabel,
                    mEditTypeAdapter, mType, mKind);
            /* @} */
        }
    }

    @Override
    public void onFieldChanged(String column, String value) {
    	
    	Log.d(TAG, "[onFieldChanged]   column=" + column + "   value="+value);
        if (!isFieldChanged(column, value)) {
            return;
        }

        // Field changes are saved directly
        saveValue(column, value);

        // Notify listener if applicable
        notifyEditorListener();

        rebuildLabel();
    }

    protected void saveValue(String column, String value) {
        mEntry.put(column, value);
    }

    /**
     * Sub classes should call this at the end of {@link #setValues} once they finish changing
     * isEmpty(). This is needed to fix b/18194655.
     */
    protected final void updateEmptiness() {
        mWasEmpty = isEmpty();
    }

    protected void notifyEditorListener() {
    	Log.d(TAG, "[notifyEditorListener]");
        if (mListener != null) {
            mListener.onRequest(EditorListener.FIELD_CHANGED);
        }
        
        boolean isEmpty = isEmpty();
        //To judge the editText empty
        if (mWasEmpty != isEmpty) {
            if (isEmpty) {
                if (mListener != null) {
                    mListener.onRequest(EditorListener.FIELD_TURNED_EMPTY);
                }
                if (mIsDeletable) mDeleteContainer.setVisibility(View.INVISIBLE);
            } else {
                if (mListener != null) {
                    mListener.onRequest(EditorListener.FIELD_TURNED_NON_EMPTY);
                }
                if (mIsDeletable) mDeleteContainer.setVisibility(View.VISIBLE);
            }
            mWasEmpty = isEmpty;

            // Update the label text color
            if (mEditTypeAdapter != null) {
                mEditTypeAdapter.notifyDataSetChanged();
            }
        }
    }

    protected boolean isFieldChanged(String column, String value) {
        final String dbValue = mEntry.getAsString(column);
        // nullable fields (e.g. Middle Name) are usually represented as empty columns,
        // so lets treat null and empty space equivalently here
        final String dbValueNoNull = dbValue == null ? "" : dbValue;
        final String valueNoNull = value == null ? "" : value;
        return !TextUtils.equals(dbValueNoNull, valueNoNull);
    }

    protected void rebuildValues() {
        setValues(mKind, mEntry, mState, mReadOnly, mViewIdGenerator);
    }

    /**
     * Prepare this editor using the given {@link DataKind} for defining structure and
     * {@link ValuesDelta} describing the content to edit. When overriding this, be careful
     * to call {@link #updateEmptiness} at the end.
     */
    @Override
    public void setValues(DataKind kind, ValuesDelta entry, RawContactDelta state, boolean readOnly,
            ViewIdGenerator vig) {
        mKind = kind;
        mEntry = entry;
        mState = state;
        mReadOnly = readOnly;
        mViewIdGenerator = vig;
        setId(vig.getId(state, kind, entry, ViewIdGenerator.NO_VIEW_INDEX));

        if (!entry.isVisible()) {
            // Hide ourselves entirely if deleted
            setVisibility(View.GONE);
            return;
        }
        setVisibility(View.VISIBLE);

        // Display label selector if multiple types available
        boolean hasTypes = RawContactModifier.hasEditTypes(kind);
        /* M: For AAS. @{ */
        if (GlobalEnv.getAasExtension().handleLabel(kind, entry, state)) {
            hasTypes = false;
        }
        // @}
        
        /*prize add-huangpengfei-2016-8-26-start*/
        String saveLocation = state.getAccountName();
        Log.d(TAG,"[setValues]  saveLocation = "+saveLocation);
        if(saveLocation != null){
	        String reg=".*SIM.*"; 
	        if(saveLocation.matches(reg)){
	        	mIsSaveInSIM = true;
	        }else{
	        	mIsSaveInSIM = false;
	        }     
        }   
        /*prize add-huangpengfei-2016-8-26-end*/
        
        /*prize-add-for dido os 8.0-hpf-2017-8-4-start*/
        String miniType = kind.mimeType;
        if(TextFieldsEditorView.MINITYPE_EMAIL.equals(miniType)){
        	mIsEmailItem = true;
        }else{
        	mIsEmailItem = false;
        }
        /*prize-add-for dido os 8.0-hpf-2017-8-4-end*/
        
        setupLabelButton(hasTypes);
        mLabel.setEnabled(!readOnly && isEnabled());
        mLabel.setContentDescription(getContext().getResources().getString(mKind.titleRes));

        if (hasTypes) {
            mType = RawContactModifier.getCurrentType(entry, kind);
            rebuildLabel();
            /*prize remove-huangliemin 2016-5-31*/
            /*
            if (mHideTypeInitially) {
                mLabel.setVisibility(View.GONE);
            }
            */
            /*prize remove-huangliemin 2016-5-31*/
        }
    }

    public ValuesDelta getValues() {
        return mEntry;
    }

    /**
     * Prepare dialog for entering a custom label. The input value is trimmed: white spaces before
     * and after the input text is removed.
     * <p>
     * If the final value is empty, this change request is ignored;
     * no empty text is allowed in any custom label.
     */
    private Dialog createCustomDialog() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        final LayoutInflater layoutInflater = LayoutInflater.from(builder.getContext());
        builder.setTitle(R.string.customLabelPickerTitle);

        final View view = layoutInflater.inflate(R.layout.contact_editor_label_name_dialog, null);
        final EditText editText = (EditText) view.findViewById(R.id.custom_dialog_content);
        editText.setInputType(INPUT_TYPE_CUSTOM);
        editText.setSaveEnabled(true);

        builder.setView(view);
        editText.requestFocus();

        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                final String customText = editText.getText().toString().trim();
                if (ContactsUtils.isGraphic(customText)) {
                    final List<EditType> allTypes =
                            RawContactModifier.getValidTypes(mState, mKind, null, true, null, true);
                    mType = null;
                    for (EditType editType : allTypes) {
                        if (editType.customColumn != null) {
                            mType = editType;
                            break;
                        }
                    }
                    if (mType == null) return;

                    mEntry.put(mKind.typeColumn, mType.rawValue);
                    mEntry.put(mType.customColumn, customText);
                    rebuildLabel();
                    requestFocusForFirstEditField();
                    onLabelRebuilt();
                }
            }
        });

        builder.setNegativeButton(android.R.string.cancel, null);

        final AlertDialog dialog = builder.create();
        dialog.setOnShowListener(new OnShowListener() {
            @Override
            public void onShow(DialogInterface dialogInterface) {
                updateCustomDialogOkButtonState(dialog, editText);
            }
        });
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateCustomDialogOkButtonState(dialog, editText);
            }
        });
        dialog.getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);

        return dialog;
    }

    /* package */ void updateCustomDialogOkButtonState(AlertDialog dialog, EditText editText) {
        final Button okButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        okButton.setEnabled(!TextUtils.isEmpty(editText.getText().toString().trim()));
    }

    /**
     * Called after the label has changed (either chosen from the list or entered in the Dialog)
     */
    protected void onLabelRebuilt() {
    }

    protected void onTypeSelectionChange(int position) {
        EditType selected = mEditTypeAdapter.getItem(position);
        /// M: For AAS. @{
        if (GlobalEnv.getAasExtension().onTypeSelectionChange(mState,
                mEntry, mKind, mEditTypeAdapter, selected, mType, getContext())) {
            Log.d(TAG, "[onTypeSelectionChange] selected:" + selected + ",mType: " + mType);
            if (Phone.TYPE_CUSTOM != selected.rawValue) {
                mType = selected;
                Log.d(TAG, "[onTypeSelectionChange] plugin selected except custom");
            }
            return;
        }
        /// @}
        // See if the selection has in fact changed
        if (mEditTypeAdapter.hasCustomSelection() && selected == CUSTOM_SELECTION) {
            return;
        }

        if (mType == selected && mType.customColumn == null) {
            return;
        }

        if (selected.customColumn != null) {
            showDialog(DIALOG_ID_CUSTOM);
        } else {
            // User picked type, and we're sure it's ok to actually write the entry.
            mType = selected;
            mEntry.put(mKind.typeColumn, mType.rawValue);
            rebuildLabel();
            requestFocusForFirstEditField();
            onLabelRebuilt();
        }
    }

    /* package */
    void showDialog(int bundleDialogId) {
        Bundle bundle = new Bundle();
        bundle.putInt(DIALOG_ID_KEY, bundleDialogId);
        getDialogManager().showDialogInView(this, bundle);
    }

    private DialogManager getDialogManager() {
        if (mDialogManager == null) {
            Context context = getContext();
            if (!(context instanceof DialogManager.DialogShowingViewActivity)) {
                throw new IllegalStateException(
                        "View must be hosted in an Activity that implements " +
                        "DialogManager.DialogShowingViewActivity");
            }
            mDialogManager = ((DialogManager.DialogShowingViewActivity)context).getDialogManager();
        }
        return mDialogManager;
    }

    @Override
    public Dialog createDialog(Bundle bundle) {
        if (bundle == null) throw new IllegalArgumentException("bundle must not be null");
        int dialogId = bundle.getInt(DIALOG_ID_KEY);
        switch (dialogId) {
            case DIALOG_ID_CUSTOM:
                return createCustomDialog();
            default:
                throw new IllegalArgumentException("Invalid dialogId: " + dialogId);
        }
    }

    protected abstract void requestFocusForFirstEditField();

    private class EditTypeAdapter extends ArrayAdapter<EditType> {
        private final LayoutInflater mInflater;
        private boolean mHasCustomSelection;
        private int mTextColorHintUnfocused;
        private int mTextColorDark;

        public EditTypeAdapter(Context context) {
            super(context, 0);
            mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            mTextColorHintUnfocused = context.getResources().getColor(
                    R.color.editor_disabled_text_color);
            mTextColorDark = context.getResources().getColor(/*R.color.primary_text_color*/R.color.spinner_color);


            if (mType != null && mType.customColumn != null) {

                // Use custom label string when present
                final String customText = mEntry.getAsString(mType.customColumn);
                if (customText != null) {
                    add(CUSTOM_SELECTION);
                    mHasCustomSelection = true;
                }
            }

            addAll(RawContactModifier.getValidTypes(mState, mKind, mType, true, null, false));
        }

        public boolean hasCustomSelection() {
            return mHasCustomSelection;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final TextView view = createViewFromResource(
                    position, convertView, parent, /*android.R.layout.simple_spinner_item*/R.layout.prize_spinner_text_view);//prize-change-hpf-2017-10-27
            // We don't want any background on this view. The background would obscure
            // the spinner's background.
            //view.setBackground(null);//prize delete huangliemin 2016-5-31
            // The text color should be a very light hint color when unfocused and empty. When
            // focused and empty, use a less light hint color. When non-empty, use a dark non-hint
            // color.
            /*prize delete huangliemin 2016-5-31 start*/
            /*
            if (!LabeledEditorView.this.isEmpty()) {
                view.setTextColor(mTextColorDark);
            } else {
                view.setTextColor(mTextColorHintUnfocused);
            }*/
            /*prize delete huangliemin 2016-5-31 end*/
            return view;
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            return createViewFromResource(
                    position, convertView, parent, android.R.layout.simple_spinner_dropdown_item);
        }

        private TextView createViewFromResource(int position, View convertView, ViewGroup parent,
                int resource) {
            TextView textView;

            if (convertView == null) {
                textView = (TextView) mInflater.inflate(resource, parent, false);
                /*prize change huangliemin-2016-5-31 start*/
                /*
                textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(
                        R.dimen.editor_form_text_size));
                textView.setTextColor(mTextColorDark);
                */
                textView.setAllCaps(true);
                textView.setGravity(Gravity.START| Gravity.CENTER_VERTICAL);
                textView.setTextAppearance(mContext, android.R.style.TextAppearance_Small);
                textView.setTextColor(mTextColorDark);
                // prize modify for bug 41591 by zhaojian 20171108 start
                //textView.setEllipsize(TruncateAt.MIDDLE);
                textView.setEllipsize(TruncateAt.END);
                // prize modify for bug 41591 by zhaojian 20171108 end
                /*prize change huangliemin-2016-5-31 end*/
                /// M: bug fix.
                textView.setHorizontalFadingEdgeEnabled(false);
            } else {
                textView = (TextView) convertView;
            }

            EditType type = getItem(position);
            /** M:AAS [COMMD_FOR_AAS]@ { */
            String text = GlobalEnv.getAasExtension()
                    .getCustomTypeLabel(type.rawValue, type.customColumn);
            Log.d(TAG, "[createViewFromResource]    GlobalEnv   text = "+text);
            if (text == null) {
            /** M: @ } */
                if (type == CUSTOM_SELECTION) {
                    text = mEntry.getAsString(mType.customColumn);
                    Log.d(TAG, "[createViewFromResource]    mEntry.getAsString   text = "+text);
                } else {
                    text = getContext().getString(type.labelRes);
                    Log.d(TAG, "[createViewFromResource]    getContext().getString   text = "+text);
                }
            }
            textView.setText(text);
            
            return textView;
        }
    }

    /**
     * M: AAS for update the tag.
     */
    public void updateValues() {
        if (mKind != null) {
            rebuildLabel();
        }
    }

    /// M: AAS debug tag.
    private static final String TAG = "LabeledEditorView";
}
