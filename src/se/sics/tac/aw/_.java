package se.sics.tac.aw;


public class _<E> {
    E ref;

    public _(E e) {
        ref = e;
    }

    public E g() {
        return ref;
    }

    public void s(E e) {
        this.ref = e;
    }

    public String toString() {
        return ref.toString();
    }
}