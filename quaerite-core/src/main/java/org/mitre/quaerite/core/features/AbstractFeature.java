package org.mitre.quaerite.core.features;



public abstract class AbstractFeature<T> implements Feature<T> {

    private final String name;

    public AbstractFeature(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

}
