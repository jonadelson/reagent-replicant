(ns toil.store)

(def identity-attrs
  "Attributes that identify an entity, and where in app-db the entities with
  that attribute live."
  {:task/id [:tasks]
   :form/id [:forms]})

(defn entity-path
  "Returns the app-db path of `entity`, based on its identity attribute."
  [entity]
  (some (fn [[attr path]]
          (when-let [id (get entity attr)]
            (conj path id)))
        identity-attrs))

(defn save
  "Merges each entity map into the entity it identifies. Keys with nil values
  are removed from the stored entity."
  [db entities]
  (reduce
   (fn [db entity]
     (let [path (or (entity-path entity)
                    (throw (ex-info "Entity has no identity attribute"
                                    {:entity entity})))
           nil-ks (keep (fn [[k v]] (when (nil? v) k)) entity)]
       (update-in db path #(apply dissoc (merge % entity) nil-ks))))
   db
   entities))
