package org.mitre.quaerite.core.features.factories;

import org.mitre.quaerite.core.features.Feature;

public abstract class AbstractFeatureFactory<T extends Feature> implements FeatureFactory<T> {

    private final String name;

    public AbstractFeatureFactory(String name) {
        this.name = name;
    }

    String getName() {
        return name;
    }
}
