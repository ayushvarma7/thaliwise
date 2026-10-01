package com.example.identify.ui;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.bumptech.glide.Glide;
import com.example.identify.App;
import com.example.identify.Config;
import com.example.identify.R;
import com.example.identify.databinding.FragmentCameraBinding;
import com.example.identify.model.ModelDownloader;
import com.example.identify.util.ImageUtil;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.IOException;

public class CameraFragment extends Fragment {

    private static final String KEY_SELECTED_IMAGE_PATH = "selectedImagePath";
    // The camera app can outlive our process, so the capture target survives recreation too.
    private static final String KEY_CAPTURE_URI = "captureUri";

    private FragmentCameraBinding binding;
    private ActivityResultLauncher<Uri> takePicture;
    private ActivityResultLauncher<PickVisualMediaRequest> pickMedia;
    private Uri captureUri;
    private String selectedImagePath;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            selectedImagePath = savedInstanceState.getString(KEY_SELECTED_IMAGE_PATH);
            String uri = savedInstanceState.getString(KEY_CAPTURE_URI);
            if (uri != null) captureUri = Uri.parse(uri);
        }
        takePicture = registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
            if (Boolean.TRUE.equals(success) && captureUri != null) prepare(captureUri);
        });
        pickMedia = registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
            if (uri != null) prepare(uri);
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentCameraBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.takePhotoButton.setOnClickListener(v -> launchCamera());
        binding.pickPhotoButton.setOnClickListener(v -> pickMedia.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build()));
        binding.identifyFab.setOnClickListener(v -> {
            if (selectedImagePath == null) return;
            Bundle b = new Bundle();
            b.putString("imagePath", selectedImagePath);
            NavHostFragment.findNavController(this).navigate(R.id.action_camera_to_result, b);
        });
        binding.openSettingsButton.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.settingsFragment));
        if (selectedImagePath != null) showSelectedImage();
    }

    @Override
    public void onResume() {
        super.onResume();
        boolean ready = ModelDownloader.filesReady(requireContext());
        binding.modelStatusText.setVisibility(ready ? View.GONE : View.VISIBLE);
        binding.openSettingsButton.setVisibility(ready ? View.GONE : View.VISIBLE);
        binding.identifyFab.setEnabled(ready);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_SELECTED_IMAGE_PATH, selectedImagePath);
        if (captureUri != null) outState.putString(KEY_CAPTURE_URI, captureUri.toString());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void launchCamera() {
        Context ctx = requireContext();
        File dir = new File(ctx.getCacheDir(), "camera");
        if (!dir.exists() && !dir.mkdirs()) Log.w(Config.LOG_TAG, "could not create " + dir);
        File f = new File(dir, "capture.jpg");
        captureUri = FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".fileprovider", f);
        takePicture.launch(captureUri);
    }

    private void prepare(Uri uri) {
        if (binding != null) binding.prepareProgress.setVisibility(View.VISIBLE);
        final Context app = requireContext().getApplicationContext();
        App.runOnIoThread(() -> {
            File prepared = null;
            try {
                prepared = ImageUtil.prepareForModel(app, uri);
            } catch (IOException | RuntimeException e) {
                Log.w(Config.LOG_TAG, "image prepare failed", e);
            }
            final File result = prepared;
            App.runOnMainThread(() -> {
                if (!isAdded() || binding == null) return;
                binding.prepareProgress.setVisibility(View.GONE);
                if (result == null) {
                    Snackbar.make(binding.getRoot(), R.string.image_prepare_failed, Snackbar.LENGTH_LONG).show();
                    return;
                }
                selectedImagePath = result.getAbsolutePath();
                showSelectedImage();
            });
        });
    }

    private void showSelectedImage() {
        Glide.with(this).load(new File(selectedImagePath)).into(binding.previewImage);
        binding.emptyHint.setVisibility(View.GONE);
        binding.identifyFab.setVisibility(View.VISIBLE);
        binding.identifyFab.setEnabled(ModelDownloader.filesReady(requireContext()));
    }
}
