(ns toil.task)

(defn add-task
  "Adds `task` to the db under the next free id."
  [db task]
  (let [id (inc (reduce max 0 (keys (:tasks db))))]
    (assoc-in db [:tasks id] (assoc task :task/id id))))

(defn get-tasks
  "Returns all tasks, newest first, or nil when there are none."
  [db]
  (->> (vals (:tasks db))
       (sort-by :task/created-at #(compare %2 %1))
       seq))
