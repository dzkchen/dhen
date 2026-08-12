#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};

in vec3 Position;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec4 Color;

out vec2 localPosition;
flat out vec2 halfExtent;
flat out float cornerRadius;
flat out float borderWidth;
out vec4 vertexColor;

const float SUBPIXEL = 8.0;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    localPosition = UV0;
    halfExtent = vec2(UV1) / SUBPIXEL;
    cornerRadius = float(UV2.x) / SUBPIXEL;
    borderWidth = float(UV2.y) / SUBPIXEL;
    vertexColor = Color;
}
