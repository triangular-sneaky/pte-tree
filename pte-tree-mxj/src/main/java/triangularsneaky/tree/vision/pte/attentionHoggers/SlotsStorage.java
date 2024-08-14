package triangularsneaky.tree.vision.pte.attentionHoggers;

import triangularsneaky.tree.vision.pte.attentionHoggers.algo.AttentionTrackingAlgoBase;
import triangularsneaky.tree.vision.pte.attentionHoggers.logging.LogManager;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class SlotsStorage {
    private final AtomicInteger timestamp;
    Map<Integer, Hoggers.AttentionSlot> idToSlots = new HashMap<>();
    ArrayList<Hoggers.AttentionSlot> slots = new ArrayList<>();
    ArrayList<Hoggers.AttentionSlot> unpinnedSlots = new ArrayList<>();

    private static final Logger log = LogManager.getLogger(SlotsStorage.class);

    public SlotsStorage(AtomicInteger timestamp) {
        this.timestamp = timestamp;
    }
//        public List<Hoggers.AttentionSlot> mapElementsToSlots(List<AttentionTrackingAlgoBase.AttentionElement> survivors) {
//            return survivors
//                    .stream()
//                    .map(s -> {
//                        var r = new Hoggers.AttentionSlot(
//                                        s.rect().x(), s.rect().y(), s.rect().w(), s.rect().h(), s.rect().area(), s.bornTimestamp());
//                        r.update(timestamp.get(), s.amplitude(), s.angle());
//                        return r;
//                    }).toList();
//        }

    public Stream<Hoggers.AttentionSlot> mapElementsToSlots(List<? extends AttentionElement> survivors) {
        // update existing and create new slots
        for (var e : survivors) {
            var slot = idToSlots.computeIfAbsent(e.getId(), id -> {
                Hoggers.AttentionSlot s = new Hoggers.AttentionSlot(e.getId(), e.rect().x(), e.rect().y(), e.rect().w(), e.rect().h(), e.rect().area(), e.bornTimestamp());
                unpinnedSlots.add(s);
                return s;
            });
            slot.update(timestamp.get(), e.amplitude(), e.angle());
        }

        // kill dead slots and replace them with new slots
        for (int i = 0; i < slots.size(); i++) {
            Hoggers.AttentionSlot s = slots.get(i);
            if (s != null && s.getLastTimestamp() != timestamp.get()) {
                idToSlots.remove(s.getId());
                s = null;
            }
            if (s == null) {
                if (!unpinnedSlots.isEmpty()) {
                    Hoggers.AttentionSlot e = unpinnedSlots.removeFirst();
                    e.pin(i);
                    slots.set(i, e);
                } else {
                    slots.set(i, null);
                }
            }
        }


        // push remaining new slots to the end
        if (!unpinnedSlots.isEmpty()) {
            log.warning("Adding extra slots: %d (%s). Did voicesCount change? \nidToSlots: %s, \nexisting slots: %s"
                    .formatted(
                            unpinnedSlots.size(),
                            String.join(",", unpinnedSlots.stream().map(x -> "%d ..%d".formatted(x.getId(), x.age())).toList()),
                            String.join(",", idToSlots.entrySet().stream().map(e->"%d->%d".formatted(e.getKey(), e.getValue().slot())).toList()),
                            String.join(",", slots.stream().map(e-> e != null ? "(%d) %d ..%d".formatted(e.slot(), e.getId(), e.age()) : "null").toList())
                    ));
        }
        while (!unpinnedSlots.isEmpty()) {
            Hoggers.AttentionSlot e = unpinnedSlots.removeFirst();
            e.pin(slots.size());
            slots.add(e);
        }

//        log.info("slots size: %s".formatted(slots.size()));

        return slots.stream().filter(Objects::nonNull);
    }
}
