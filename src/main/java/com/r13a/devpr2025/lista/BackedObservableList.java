package com.r13a.devpr2025.lista;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.logging.Logger;

public class BackedObservableList<T> implements List<T> {
    private static final Logger logger = Logger.getLogger(BackedObservableList.class.getName());
    private static int current = 0;
    private final List<BackedListListener> listeners = new ArrayList();

    public void addListener(BackedListListener<T> listener) {
        logger.info(">>---> adicionado listener");
        listeners.add(listener);
    }

    public void removeListener(BackedListListener<T> listener) {
        logger.info(">>---> removido listener");
        listeners.remove(listener);
    }

    private final List<T> backed;

    public BackedObservableList() {
        backed = new ArrayList();
    }

    public BackedObservableList(List<T> backed) {
        this.backed = backed;
    }

    private void notifyListeners(ListChangeEvent<T> event) {

        if (listeners.size() == 0)
            return;

        if (current >= listeners.size())
            current = 0;

        try{
            listeners.get(current).setOnChanged(event);
        } catch (IndexOutOfBoundsException e) {
            current = 0;
            try {
                listeners.get(0).setOnChanged(event);
            } catch (Exception y) { 
                //ignora 
                }
        }
        current++;

        //for (BackedListListener<T> listener : listeners) {
          //  listener.setOnChanged(event);
        //}
    }

    @Override
    public boolean add(T e) {
        if (backed.add(e)) {
            ListChangeEvent<T> event = new ListChangeEvent(this, backed.indexOf(e), backed.indexOf(e) + 1, true, e);
            notifyListeners(event);
            return true;
        }
        return false;
    }

    @Override
    public boolean remove(Object o) {
        if (backed.remove(o)) {
            ListChangeEvent<T> event = new ListChangeEvent(this, backed.indexOf(o), backed.indexOf(o) + 1, false, o);
            notifyListeners(event);
            return true;
        }
        return false;
    }

    /*
     * 
     * The iterator seemed easy enough, until I remembered the iterator.remove()
     * call.
     * I still haven't fully tested it (it works, but only as far as I've used it)
     * 
     */
    @Override
    public Iterator<T> iterator() {
        return new Iterator<T>() {
            T currentItem = null;
            int currentIndex = 0;

            @Override
            public boolean hasNext() {
                return backed.size() > currentIndex;
            }

            @Override
            public T next() {

                return currentItem = backed.get(currentIndex++);
            }

            @Override
            public void remove() {
                if (backed.remove(currentItem)) {
                    currentIndex--;
                    notifyListeners(new ListChangeEvent<T>(backed, currentIndex, currentIndex + 1, false, currentItem));
                }
            }
        };
    }

    @Override
    public void add(int index, T element) {
        backed.add(index, element);
        ListChangeEvent<T> event = new ListChangeEvent(this, backed.indexOf(element), backed.indexOf(element) + 1, true,
                element);
        notifyListeners(event);
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        if (backed.addAll(c)) {
            ListChangeEvent<T> event = new ListChangeEvent(this, backed.indexOf(c), backed.indexOf(c) + 1, true, c);
            notifyListeners(event);
            return true;
        }
        return false;
    }

    @Override
    public boolean addAll(int index, Collection<? extends T> c) {
        if (backed.addAll(c)) {
            ListChangeEvent<T> event = new ListChangeEvent(this, backed.indexOf(c), backed.indexOf(c) + 1, true, c);
            notifyListeners(event);
            return true;
        }
        return false;
    }

    @Override
    public void clear() {
        backed.clear();
        ListChangeEvent<T> event = new ListChangeEvent(this);
        notifyListeners(event);

    }

    @Override
    public boolean contains(Object o) {
        return backed.contains(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return containsAll(c);
    }

    @Override
    public T get(int index) {
        return backed.get(index);
    }

    @Override
    public int indexOf(Object o) {
        return backed.indexOf(o);
    }

    @Override
    public boolean isEmpty() {
        return backed.isEmpty();
    }

    @Override
    public int lastIndexOf(Object o) {
        return backed.lastIndexOf(o);
    }

    @Override
    public ListIterator<T> listIterator() {
        return backed.listIterator();
    }

    @Override
    public ListIterator<T> listIterator(int index) {
        return backed.listIterator(index);
    }

    @Override
    public T remove(int index) {
        T item = backed.remove(index);
        if (item != null) {
            ListChangeEvent<T> event = new ListChangeEvent(this, index, index + 1, false, item);
            notifyListeners(event);
            return item;
        }
        return null;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        if (backed.removeAll(c)) {
            ListChangeEvent<T> event = new ListChangeEvent(this);
            notifyListeners(event);
            return true;
        }
        return false;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        if (backed.retainAll(c)) {
            ListChangeEvent<T> event = new ListChangeEvent(this, 0, 0, false, c);
            notifyListeners(event);
            return true;
        }
        return false;
    }

    @Override
    public T set(int index, T element) {
        T item = backed.set(index, element);
        if (item != null) {
            ListChangeEvent<T> event = new ListChangeEvent(this, index, index + 1, false, element);
            notifyListeners(event);
            return item;
        }
        return null;
    }

    @Override
    public int size() {
        return backed.size();
    }

    @Override
    public List<T> subList(int fromIndex, int toIndex) {
        return backed.subList(fromIndex, toIndex);
    }

    @Override
    public Object[] toArray() {
        return backed.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return backed.toArray(a);
    }
}
