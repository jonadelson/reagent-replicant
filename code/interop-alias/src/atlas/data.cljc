(ns atlas.data)

(def cities
  [{:id "san-francisco"
    :name "San Francisco"
    :position [-122.475238, 37.807962]
    :zoom 11
    :points
    [{:point/label "Bulbasaur"
      :point/latitude 37.807962
      :point/longitude -122.475238}
     {:point/label "Charmander"
      :point/latitude 34.062759
      :point/longitude -118.35718}
     {:point/label "Squirtle"
      :point/latitude 37.805929
      :point/longitude -122.429582}
     {:point/label "Magnemite"
      :point/latitude 37.8269775
      :point/longitude -122.425144}
     {:point/label "Magmar"
      :point/latitude 37.571414
      :point/longitude -122.00004}]}
   {:id "london"
    :name "London"
    :position [-0.1276, 51.5072]
    :zoom 12
    :points
    [{:point/label "Pikachu"
      :point/latitude 51.5081
      :point/longitude -0.1281}
     {:point/label "Eevee"
      :point/latitude 51.5074
      :point/longitude -0.1657}
     {:point/label "Snorlax"
      :point/latitude 51.5081
      :point/longitude -0.0759}
     {:point/label "Gengar"
      :point/latitude 51.5663
      :point/longitude -0.1464}
     {:point/label "Lapras"
      :point/latitude 51.5055
      :point/longitude -0.0754}]}
   {:id "tokyo"
    :name "Tokyo"
    :position [139.6917, 35.6895]
    :zoom 12}
   {:id "cape-town"
    :name "Cape Town"
    :position [18.4241, -33.9249]
    :zoom 11}])
