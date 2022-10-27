package com.autocoin.cap;

import java.util.ArrayList;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Show various scenarios what happens when equals-hashCode contract is broken.
 * Contract: 2 equal objects should have the same hashCode
 * <p>
 * Broken equals-hashCode contract does not affect ArrayList behaviour.
 */
public class EqualsAndHashcodeTest {
    private static final Logger logger = LoggerFactory.getLogger(EqualsAndHashcodeTest.class);

    class ImmutablePersonBrokenHashcode {
        private final String name;
        private final String surname;
        private final String number;

        ImmutablePersonBrokenHashcode(String name, String surname, String number) {
            this.name = name;
            this.surname = surname;
            this.number = number;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ImmutablePersonBrokenHashcode that = (ImmutablePersonBrokenHashcode) o;

            if (!name.equals(that.name)) return false;
            return surname.equals(that.surname);
        }

        @Override
        public int hashCode() {
            return number.hashCode();
        }

        @Override
        public String toString() {
            return "ImmutablePersonBrokenHashcode{" +
                    "name='" + name + '\'' +
                    ", surname='" + surname + '\'' +
                    ", number='" + number + '\'' +
                    '}';
        }
    }

    class MutablePersonBrokenHashcode {
        private String name;
        private String surname;
        private String number;

        MutablePersonBrokenHashcode(String name, String surname, String number) {
            this.name = name;
            this.surname = surname;
            this.number = number;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            MutablePersonBrokenHashcode that = (MutablePersonBrokenHashcode) o;

            if (!name.equals(that.name)) return false;
            return surname.equals(that.surname);
        }

        @Override
        public int hashCode() {
            return number.hashCode();
        }

        @Override
        public String toString() {
            return "MutablePersonBrokenHashcode{" +
                    "name='" + name + '\'' +
                    ", surname='" + surname + '\'' +
                    ", number='" + number + '\'' +
                    '}';
        }
    }

    private final ImmutablePersonBrokenHashcode johnRambo0 = new ImmutablePersonBrokenHashcode("John", "Rambo", "0");
    private final ImmutablePersonBrokenHashcode johnRambo1 = new ImmutablePersonBrokenHashcode("John", "Rambo", "1");

    @Test
    public void shouldAddEqualObjectSecondTimeToHashSetWhenBrokenHashCode() {
        // given
        var set = new HashSet<ImmutablePersonBrokenHashcode>();
        // when
        assertTrue(set.add(johnRambo0));
        // then
        assertTrue(set.add(johnRambo1)); // unexpected behaviour, equal object should be unique and not added second time
    }

    @Test
    public void shouldNotRemoveEqualObjectFromHashSetWhenBrokenHashCode() {
        // given
        var set = new HashSet<ImmutablePersonBrokenHashcode>();
        // when
        assertTrue(set.add(johnRambo0));
        // then
        assertFalse(set.remove(johnRambo1)); // unexpected behaviour, equal object should be removed
    }


    @Test
    public void shouldRemoveEqualObjectFromArrayListWhenBrokenHashCode() {
        // given
        var list = new ArrayList<ImmutablePersonBrokenHashcode>();
        // when
        assertTrue(list.add(johnRambo0));
        assertTrue(list.add(johnRambo0));
        // then
        // lists has different implementation from hash set, behaviour is not broken
        assertTrue(list.remove(johnRambo1));
        assertEquals(1, list.size());
    }

    @Test
    public void shouldNotContainEqualObjectFromHashSetWhenMutatedHashCode() {
        // given
        var johnWayne = new MutablePersonBrokenHashcode("John", "Wayne", "0");
        var set = new HashSet<MutablePersonBrokenHashcode>();
        // when
        assertTrue(set.add(johnWayne));
        assertTrue(set.contains(johnWayne));
        johnWayne.number = "999"; // change the hashCode
        // then
        assertFalse(set.contains(johnWayne)); // unexpected behaviour
    }

    @Test
    public void shouldAddEqualObjectSecondTimeToHashSetWhenMutatedHashCode() {
        // given
        var johnWayne = new MutablePersonBrokenHashcode("John", "Wayne", "0");
        var set = new HashSet<MutablePersonBrokenHashcode>();
        // when
        assertTrue(set.add(johnWayne));
        johnWayne.number = "999"; // change the hashCode
        // then
        assertTrue(set.add(johnWayne)); // unexpected behaviour, the same object should not be added second time
        assertEquals(2, set.size());
    }


}
