async function consultarCPF() {

    const cpf = document.getElementById("cpf").value;

    const resposta = await fetch(`/cpf/${cpf}`);

    const texto = await resposta.text();

    document.getElementById("resultado").innerHTML = texto;
}