package br.com.cursomc.services;

import java.awt.image.BufferedImage;
import java.net.URI;
import java.util.List;

import javax.transaction.Transactional;
import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import br.com.cursomc.domains.Cidade;
import br.com.cursomc.domains.Cliente;
import br.com.cursomc.domains.Endereco;
import br.com.cursomc.dto.ClienteDTO;
import br.com.cursomc.dto.ClienteNewDTO;
import br.com.cursomc.enums.Perfil;
import br.com.cursomc.enums.TipoCliente;
import br.com.cursomc.exceptions.AuthorizationException;
import br.com.cursomc.exceptions.ObjectNotFoundException;
import br.com.cursomc.repositories.ClienteRepository;
import br.com.cursomc.repositories.EnderecoRepository;
import br.com.cursomc.security.UserSS;

@Service
public class ClienteService {

	@Autowired
	private BCryptPasswordEncoder pe;

	@Autowired
	private ClienteRepository clienteRepository;

	@Autowired
	private EnderecoRepository enderecoRepository;

	@Autowired
	private S3Service s3Service;

	@Autowired
	private ImageService imageService;

	@Value("${img.prefix.client.profile}")
	private String prefix;

	@Value("${img.profile.size}")
	private Integer size;

	public Cliente find(Integer id) {
		UserSS user = UserService.authenticated();
		if (user == null || !user.hasRole(Perfil.ADMIN) && !id.equals(user.getId())) {
			throw new AuthorizationException("Não autorizado");
		}

		return clienteRepository.findById(id).orElseThrow(() -> new ObjectNotFoundException("Id não encontrado."));
	}

	public Cliente fromDTO(ClienteDTO objDto) {
		return new Cliente(objDto.getId(), objDto.getNome(), objDto.getEmail(), null, null, null);
	}

	public List<Cliente> findAll() {
		return clienteRepository.findAll();
	}

	public Cliente findByEmail(String email) {
		UserSS user = UserService.authenticated();
		if (user == null || !user.hasRole(Perfil.ADMIN) && !email.equals(user.getUsername())) {
			throw new AuthorizationException("Não autorizado");
		}

		return clienteRepository.findByEmail(email);
	}

	@Transactional
	public Cliente insert(Cliente obj) {
		obj.setId(null);
		enderecoRepository.saveAll(obj.getEnderecos());
		return clienteRepository.save(obj);
	}

	public Page<Cliente> findPage(Integer page, Integer linesPerPage, String orderBy, String direction) {
		PageRequest pageRequest = PageRequest.of(page, linesPerPage, Direction.valueOf(direction), orderBy);
		return clienteRepository.findAll(pageRequest);
	}

	public void delete(Integer id) {
		clienteRepository.deleteById(id);
	}

	public Cliente update(Cliente obj, Integer id) {
		Cliente c = find(id);
		updateData(obj, c);
		return clienteRepository.save(c);
	}

	private void updateData(Cliente obj, Cliente c) {
		c.setNome(obj.getNome());
		c.setEmail(obj.getEmail());
	}

	public Cliente fromDTO(@Valid ClienteNewDTO objDto) {
		Cliente cli = new Cliente(null, objDto.getNome(), objDto.getEmail(), objDto.getCpfOuCnpj(),
				TipoCliente.toEnum(objDto.getTipoCliente()), pe.encode(objDto.getSenha()));
		Cidade cid = new Cidade(objDto.getCidadeId(), null, null);
		Endereco ed = new Endereco(null, objDto.getLogradouro(), objDto.getNumero(), objDto.getComplemento(),
				objDto.getBairro(), objDto.getCep(), cli, cid);
		cli.getEnderecos().add(ed);
		cli.getTelefones().add(objDto.getTelefone1());
		if (objDto.getTelefone2() != null) {
			cli.getTelefones().add(objDto.getTelefone2());
		}
		if (objDto.getTelefone3() != null) {
			cli.getTelefones().add(objDto.getTelefone3());
		}
		return cli;
	}

	public void save(Cliente c) {
		clienteRepository.save(c);
	}

	public URI uploadProfilePicture(MultipartFile multipartFile) {
		UserSS user = UserService.authenticated();

		if (user == null) {
			throw new AuthorizationException("Acesso negado.");
		}

		BufferedImage jpgImage = imageService.getJpgImageFromFile(multipartFile);
		jpgImage = imageService.cropSquare(jpgImage);
		jpgImage = imageService.resize(jpgImage, size);

		String fileName = prefix + user.getId() + ".jpg";

		return s3Service.uploadFile(imageService.getInputStrem(jpgImage, "jpg"), fileName, "image");
	}

}
