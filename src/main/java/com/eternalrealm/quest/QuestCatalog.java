package com.eternalrealm.quest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * An immutable, validated set of quests. Ids are unique and a quest may only require quests listed <em>before</em> it,
 * which keeps the prerequisite graph acyclic by construction and makes the list order a valid progression order.
 * References to other modules (monsters, items, dungeons, NPCs) are checked by {@link Quests#referenceProblems}.
 */
public final class QuestCatalog {
    private final List<QuestDefinition> quests;
    private final Map<String, QuestDefinition> byId;

    public QuestCatalog(List<QuestDefinition> quests) {
        this.quests = List.copyOf(quests);
        Map<String, QuestDefinition> index = new HashMap<>();
        for (QuestDefinition quest : this.quests) {
            for (String required : quest.prerequisites().questIds()) {
                if (!index.containsKey(required)) {
                    throw new IllegalArgumentException(quest.id() + " requires " + required
                            + ", which is not defined before it");
                }
            }
            if (index.put(quest.id(), quest) != null) {
                throw new IllegalArgumentException("duplicate quest id " + quest.id());
            }
        }
        this.byId = Map.copyOf(index);
    }

    /** All quests in catalog (= progression) order. */
    public List<QuestDefinition> all() {
        return quests;
    }

    public Optional<QuestDefinition> byId(String id) {
        return Optional.ofNullable(byId.get(Objects.requireNonNull(id, "id")));
    }

    public List<QuestDefinition> ofKind(QuestKind kind) {
        return quests.stream().filter(quest -> quest.kind() == kind).toList();
    }

    /** The MAIN quests in story order. */
    public List<QuestDefinition> mainChain() {
        return ofKind(QuestKind.MAIN);
    }

    public int size() {
        return quests.size();
    }
}
