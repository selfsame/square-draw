(ns ^:figwheel-hooks square-draw.core
  (:require
    [reagent.core :as r]
    [reagent.dom :as rd]))

(defonce state (r/atom {
  :tool :draw
  }))

(defn toolbar-ui []
  [:div.toolbar
    (into [:div]
      (map 
        (fn [k] 
          [:div.tool 
            {:class (if (= (:tool @state) k) "active")
             :on-click (fn [e] (swap! state assoc :tool k))}
            k])
        [:move :draw]))])

(defn root-ui []
  [:div
    [toolbar-ui]])

(rd/render [root-ui] (js/document.querySelector "#app"))