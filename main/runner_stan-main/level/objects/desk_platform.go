components {
  id: "platform"
  component: "/main/runner_stan-main/level/objects/desk_platform.script"
}
embedded_components {
  id: "sprite"
  type: "sprite"
  data: "default_animation: \"rock\"\n"
  "material: \"/builtins/materials/sprite.material\"\n"
  "textures {\n"
  "  sampler: \"texture_sampler\"\n"
  "  texture: \"/main/runner_stan-main/level/level.atlas\"\n"
  "}\n"
  ""
}
embedded_components {
  id: "collisionobject"
  type: "collisionobject"
  data: "type: COLLISION_OBJECT_TYPE_KINEMATIC\n"
  "mass: 0.0\n"
  "friction: 0.1\n"
  "restitution: 0.5\n"
  "group: \"geometry\"\n"
  "mask: \"hero\"\n"
  "embedded_collision_shape {\n"
  "  shapes {\n"
  "    shape_type: TYPE_BOX\n"
  "    position {\n"
  "    }\n"
  "    rotation {\n"
  "    }\n"
  "    index: 0\n"
  "    count: 3\n"
  "    id: \"Box\"\n"
  "  }\n"
  "  data: 194.54225\n"
  "  data: 82.45355\n"
  "  data: 10.0\n"
  "}\n"
  ""
}
embedded_components {
  id: "spikes_center"
  type: "sprite"
  data: "default_animation: \"rock\"\n"
  "material: \"/builtins/materials/sprite.material\"\n"
  "textures {\n"
  "  sampler: \"texture_sampler\"\n"
  "  texture: \"/main/runner_stan-main/level/level.atlas\"\n"
  "}\n"
  ""
  position {
    y: -40.0
    z: -0.1
  }
  scale {
    x: 1.2
    y: 1.2
    z: 1.0
  }
}
embedded_components {
  id: "spikes_left"
  type: "sprite"
  data: "default_animation: \"rock\"\n"
  "material: \"/builtins/materials/sprite.material\"\n"
  "textures {\n"
  "  sampler: \"texture_sampler\"\n"
  "  texture: \"/main/runner_stan-main/level/level.atlas\"\n"
  "}\n"
  ""
  position {
    x: -220.0
    y: -50.0
    z: -0.1
  }
}
embedded_components {
  id: "spikes_right"
  type: "sprite"
  data: "default_animation: \"rock\"\n"
  "material: \"/builtins/materials/sprite.material\"\n"
  "textures {\n"
  "  sampler: \"texture_sampler\"\n"
  "  texture: \"/main/runner_stan-main/level/level.atlas\"\n"
  "}\n"
  ""
  position {
    x: 220.0
    y: -50.0
    z: -0.1
  }
}
embedded_components {
  id: "danger_edges"
  type: "collisionobject"
  data: "type: COLLISION_OBJECT_TYPE_KINEMATIC\n"
  "mass: 0.0\n"
  "friction: 0.1\n"
  "restitution: 0.5\n"
  "group: \"danger\"\n"
  "mask: \"hero\"\n"
  "embedded_collision_shape {\n"
  "  shapes {\n"
  "    shape_type: TYPE_SPHERE\n"
  "    position {\n"
  "      x: -190.0\n"
  "      y: -10.0\n"
  "    }\n"
  "    rotation {\n"
  "    }\n"
  "    index: 0\n"
  "    count: 1\n"
  "    id: \"left_cone\"\n"
  "  }\n"
  "  shapes {\n"
  "    shape_type: TYPE_SPHERE\n"
  "    position {\n"
  "      x: 0.0\n"
  "      y: -10.0\n"
  "    }\n"
  "    rotation {\n"
  "    }\n"
  "    index: 1\n"
  "    count: 1\n"
  "    id: \"center_cone\"\n"
  "  }\n"
  "  shapes {\n"
  "    shape_type: TYPE_SPHERE\n"
  "    position {\n"
  "      x: 190.0\n"
  "      y: -10.0\n"
  "    }\n"
  "    rotation {\n"
  "    }\n"
  "    index: 2\n"
  "    count: 1\n"
  "    id: \"right_cone\"\n"
  "  }\n"
  "  data: 40.0\n"
  "  data: 40.0\n"
  "  data: 40.0\n"
  "}\n"
  ""
}
embedded_components {
  id: "extra_credit_factory"
  type: "factory"
  data: "prototype: \"/main/runner_stan-main/level/objects/extra_credit.go\"\n"
  ""
}
