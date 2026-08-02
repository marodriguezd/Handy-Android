package com.handy.android;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelStoreOwner;
import androidx.savedstate.SavedStateRegistryOwner;

/**
 * Compose 1.11 requires the {@code ViewTree*} owners to be present on ComposeViews
 * placed in windows that are not attached to an Activity (IME input view, overlays).
 * Without them it throws "Composed into the View which doesn't propagate
 * ViewTreeLifecycleOwner!".
 *
 * <p>The lifecycle classes themselves ({@code Lifecycle}, {@code SavedStateRegistryOwner},
 * {@code ViewModelStore}) resolve fine, but the android-variant symbols they need
 * ({@code androidx.lifecycle.ViewTreeLifecycleOwner}, {@code ViewTreeViewModelStoreOwner},
 * {@code androidx.savedstate.ViewTreeSavedStateRegistryOwner}) are not visible to the
 * Kotlin compiler from the KMP androidx artifacts — they are plain public bytecode, so
 * Java can call them. The wiring therefore lives in this bridge.
 */
public final class ViewTreeOwnerBridge {

    private ViewTreeOwnerBridge() {
    }

    public static void attach(
            @NonNull View view,
            @NonNull LifecycleOwner lifecycleOwner,
            @NonNull ViewModelStoreOwner viewModelStoreOwner,
            @NonNull SavedStateRegistryOwner savedStateRegistryOwner) {
        androidx.lifecycle.ViewTreeLifecycleOwner.set(view, lifecycleOwner);
        androidx.lifecycle.ViewTreeViewModelStoreOwner.set(view, viewModelStoreOwner);
        androidx.savedstate.ViewTreeSavedStateRegistryOwner.set(view, savedStateRegistryOwner);
    }

    @Nullable
    public static LifecycleOwner lifecycleOwner(@NonNull View view) {
        return androidx.lifecycle.ViewTreeLifecycleOwner.get(view);
    }

    @Nullable
    public static SavedStateRegistryOwner savedStateRegistryOwner(@NonNull View view) {
        return androidx.savedstate.ViewTreeSavedStateRegistryOwner.get(view);
    }
}
