package co.tz.travelmantics;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.OnProgressListener;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.squareup.picasso.Picasso;


public class DealActivity extends AppCompatActivity {

    private FirebaseDatabase mFirebaseDatabase;
    private DatabaseReference mDatabaseReference;
    private static final int PIC_RESULT = 42;
    EditText txtTitle;
    EditText txtDescription;
    EditText txtPrice;
    TravelDeal deal;
    Button uploadBtn;
    ImageView imageView;
    ProgressDialog dialog;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_deal);
        mFirebaseDatabase = FirebaseUtil.mFirebaseDatabase;
        mDatabaseReference = FirebaseUtil.mDatabaseReference;
        txtTitle = findViewById(R.id.txtTitle);
        txtDescription = findViewById(R.id.txtDescription);
        txtPrice = findViewById(R.id.txtPrice);
        imageView = findViewById(R.id.image);

        Intent intent = getIntent();
        deal = (TravelDeal) intent.getSerializableExtra("Deal");
        if (deal == null) {
            deal = new TravelDeal();
        }
        this.deal = deal;
        txtTitle.setText(deal.getTitle());
        txtDescription.setText(deal.getDescription());
        txtPrice.setText(deal.getPrice());
        showImage(deal.getImageUrl());
        uploadBtn = findViewById(R.id.btnImage);
        uploadBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/jpeg");
                intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
                startActivityForResult(intent.createChooser(intent, "Insert Picture"), PIC_RESULT);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PIC_RESULT && resultCode == RESULT_OK) {
            Uri imageUri = data.getData();
            dialog = new ProgressDialog(DealActivity.this);
            dialog.setMessage("Uploading...");
            dialog.setIndeterminate(false);
            dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            dialog.setProgress(0);
            final StorageReference ref = FirebaseUtil.mStorageRef.child(imageUri.getLastPathSegment());
            ref.putFile(imageUri).addOnProgressListener(new OnProgressListener<UploadTask.TaskSnapshot>() {
                @Override
                public void onProgress(UploadTask.TaskSnapshot taskSnapshot) {

                    dialog.show();

                        double progress = (taskSnapshot.getBytesTransferred()/taskSnapshot.getTotalByteCount())*100;
                        String progressText = taskSnapshot.getBytesTransferred()/1024+"KBs/" + taskSnapshot.getTotalByteCount() + "Kbs";
                        dialog.setProgress((int)progress);


                }
            }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {

                @Override
                public void onSuccess(final UploadTask.TaskSnapshot taskSnapshot) {

                    taskSnapshot.getMetadata().getReference().getDownloadUrl().addOnCompleteListener(new OnCompleteListener<Uri>() {
                        @Override
                        public void onComplete(@NonNull Task<Uri> task) {
                            dialog.dismiss();
                            String url = task.getResult().toString();
                            deal.setImageUrl(url);
                            deal.setImageName(taskSnapshot.getStorage().getPath());
                            Toast.makeText(getApplicationContext(), "Image uploaded", Toast.LENGTH_LONG).show();

                            showImage(url);
                        }
                    });
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    dialog.dismiss();
                    Toast.makeText(getApplicationContext(), "Image upload Failed", Toast.LENGTH_LONG).show();

                }
            });
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.save_menu:
                saveDeal();
                return true;
            case R.id.delete_menu:
                deleteDeal();
                return true;
             default:
                 return super.onOptionsItemSelected(item);
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.save_menu, menu);
        if (FirebaseUtil.isAdmin == true) {
            menu.findItem(R.id.delete_menu).setVisible(true);
            menu.findItem(R.id.save_menu).setVisible(true);
            enableEditTexts(true);
        }
        else  {
            menu.findItem(R.id.delete_menu).setVisible(false);
            menu.findItem(R.id.save_menu).setVisible(false);
            enableEditTexts(false);
        }
        return  true;
    }

    private void clean() {
        txtPrice.setText("");
        txtTitle.setText("");
        txtDescription.setText("");
        txtTitle.requestFocus();
        imageView.setImageURI(Uri.parse(""));
    }

    private void saveDeal() {
        if(txtTitle.length() == 0) {
            txtTitle.setError("Please enter title");
        }  else if (txtPrice.length() == 0) {
            txtPrice.setError("Please enter price");
        }else if(txtDescription.length() == 0) {
            txtDescription.setError("Please enter description");
        } else  {
            deal.setTitle(txtTitle.getText().toString());
            deal.setDescription(txtDescription.getText().toString());
            deal.setPrice(txtPrice.getText().toString());
            if (deal.getId() == null) {
                mDatabaseReference.push().setValue(deal);
            } else {
                mDatabaseReference.child(deal.getId()).setValue(deal);
            }
            Toast.makeText(this, "Deal Saved", Toast.LENGTH_LONG).show();
            clean();
            backToList();
        }
    }

    private void deleteDeal() {
        if ((deal.getTitle() == null) && (deal.getDescription() == null) && (deal.getPrice() == null)) {
            Toast.makeText(this, "Please save deal before deleting", Toast.LENGTH_SHORT).show();
            return ;
        }
        if (deal.getImageName() != null && deal.getImageName().isEmpty() == false) {
            Log.d("Image Name", deal.getImageName());
            StorageReference picRef = FirebaseUtil.mStorage.getReference().child(deal.getImageName());
            picRef.delete().addOnSuccessListener(new OnSuccessListener<Void>() {
                @Override
                public void onSuccess(Void aVoid) {
                    Log.d("Image Delete", "Successful");
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Toast.makeText(getApplicationContext(), "Delete Failed", Toast.LENGTH_LONG).show();
                    Log.d("Image Delete", e.getMessage());
                }
            });
        }
        mDatabaseReference.child(deal.getId()).removeValue();
        Toast.makeText(this, "Deal Deleted", Toast.LENGTH_LONG).show();
        backToList();

    }

    private void backToList() {
        Intent intent = new Intent(this, ListActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    private void enableEditTexts(boolean isEnabled) {
        txtPrice.setEnabled(isEnabled);
        txtDescription.setEnabled(isEnabled);
        txtTitle.setEnabled(isEnabled);
        if (!isEnabled)
        { uploadBtn.setVisibility(View.INVISIBLE);}


    }

    private void showImage(String url) {
        if (url != null && url.isEmpty() == false) {
            int width = Resources.getSystem().getDisplayMetrics().widthPixels;
            Picasso.with(this)
                    .load(url)
                    .resize(width, width*2/3)
                    .centerCrop()
                    .into(imageView);
        }
    }
}
