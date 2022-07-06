(ns reacl-c.main-browser-test
  (:require [reacl-c.main :as main]
            [reacl-c.core :as c]
            [reacl-c.dom :as dom]
            [clojure.string :as str]
            [active.clojure.lens :as lens]
            [schema.core :as s :include-macros true]
            [active.clojure.functions :as f]
            ["react-dom/test-utils" :as react-tu]))

;; catching and testing for error doesn't work properly in karma/compiled mode of shadow
(def karma? (not= (aget js/window "__karma__") js/undefined))

(defn run-in [host item & [state handle-action!]]
  (main/run host item {:initial-state state
                       :handle-action! handle-action!}))

(defn render [item & [state handle-action!]]
  (let [host (js/document.createElement "div")]
    [(run-in host item state handle-action!)
     host]))

(defn renders-as* [item & [state handle-action!]]
  (let [[app host] (render item state handle-action!)]
    (array-seq (.-childNodes host))))

(defn renders-as [item & [state handle-action!]]
  (first (renders-as* item state handle-action!)))

(defn injector
  "Returns an item and a function that, when called with a host dom node
  in which the item is rendered and a function f, calls f as an event
  handler function `(f state)`."
  []
  (let [ret (atom nil)
        class (str (gensym "injector"))
        item (dom/button {:class class
                          :onClick (fn [state ev]
                                     (@ret state))})]
    [item (fn [host f]
            (reset! ret f)
            (let [n (first (array-seq (.getElementsByClassName host class)))]
              (assert (some? n) "injector item not found")
              (react-tu/Simulate.click n))
            (reset! ret nil))]))

(defn tag [node]
  (str/lower-case (.-tagName node)))

(defn text [node]
  (.-textContent node))

(defn passes-messages
  "Calls f with an item and returns if the returned item passes messages sent to it down to that item."
  [f]
  (let [[x inject!] (injector)
        it (c/with-ref
             (fn [ref]
               (dom/div (c/fragment
                         (c/dynamic str)
                         (-> x
                             (c/handle-action (fn [_ a]
                                                (c/return :message [ref (:res a)])))))
                        (-> (f (c/handle-message (fn [state msg]
                                                   (c/return :action msg))
                                                 c/empty))
                            (c/refer ref)
                            (c/handle-action (fn [_ a]
                                               (c/return :state a)))))))
        node (renders-as it "start")]

    (inject! node (constantly (c/return :action {:res "ok"})))
    (= "ok" (text (.-firstChild node)))))

(defn passes-actions
  "Calls f with an item and returns if an action emitted by that item is passed up by the returned item."
  [f]
  (let [[x inject!] (injector)
        it (c/with-state-as st
             (dom/div st (-> (f x)
                             (c/handle-action (fn [_ a]
                                                (c/return :state a))))))
        node (renders-as it "start")]

    (inject! node (constantly (c/return :action "ok")))
    (= "ok" (text (.-firstChild node))))
  )


